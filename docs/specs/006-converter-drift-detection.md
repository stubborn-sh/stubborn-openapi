# Feature 6: Inline Drift Detection in OpenAPI Contract Converter

## What

When `OpenApiContractConverter` parses an OpenAPI 3.x YAML/JSON file and produces
Spring Cloud Contract DSL contracts, the contracts are now also verified against
the same OpenAPI specification using `OpenApiContractsVerifier`. Drift between
the generated contracts and the source spec is surfaced — either as logged
warnings or as a hard failure, depending on configuration.

## Why

`@VerifyContractsAgainstOpenApi` plus `OpenApiContractsVerifierExtension` already
detect drift, but only when a test class explicitly opts in. When SCC, a Maven
plugin, or any other consumer goes through the `ContractConverter` SPI to load
contracts from an OpenAPI file, no drift detection runs at all. That means:

- Converter bugs that produce contracts inconsistent with the source spec ship
  silently.
- Users of the CLI / SPI path get a weaker guarantee than users of the JUnit
  extension, even though they parsed the same OpenAPI file.
- Failures only show up later, deep inside SCC stub generation, with confusing
  errors.

Running the verifier inline closes this gap: every code path that produces
contracts from an OpenAPI source gets the same drift guarantee.

## How (High Level)

`OpenApiContractConverter.convertFrom(File)` parses the OpenAPI spec exactly
once via `Oa3Parser` and converts it into a stream of contract YAML strings,
then into `Contract` objects. After conversion, the converter passes the
already-parsed `OpenAPI` model into a dedicated
`OpenApiContractsVerifier.verifyInMemory(OpenAPI, Collection<Contract>)`
overload — no second read of the file, no re-parse — and the verifier runs
both path/method/status drift checks and schema-level drift checks against
that single model.

### SSRF mitigation

OpenAPI parsing is performed via a hardened `OpenApiSafeParser` utility that
sets `ParseOptions.setResolve(false)` (plus the related
`setResolveFully`/`setResolveCombinators`/`setResolveRequestBody`/`setResolveResponses`
to `false`). A malicious spec containing
`$ref: 'https://attacker.invalid/x'` cannot make the build JVM open outbound
connections. Local `#/components/...` references are still resolved by the
downstream Atlassian validator at validation time — disabling parser-side
resolution does not break schema-level drift checks.

Verification covers **every drift axis the OpenAPI document can describe**:

| Axis | What is checked |
|------|------------------|
| Path | Contract URL matches an OpenAPI path template (segment-by-segment). |
| Method | OpenAPI operation exists for that path + method. |
| Response status | OpenAPI declares the contract's response status (incl. `2XX` / `default`). |
| Request body schema | Contract request body validates against `requestBody.content[*].schema`, with `$ref` resolution. |
| Response body schema | Contract response body validates against `responses[status].content[*].schema`. |
| Request `Content-Type` | Contract `Content-Type` header is one of the spec's declared `requestBody.content` media types. |
| Response `Content-Type` | Contract response `Content-Type` is one of `responses[status].content` media types. |
| Required request headers | Every required header in the spec's `parameters[in=header]` is present on the contract. |
| Required response headers | Every required header in `responses[status].headers` is present on the contract response. |
| Query parameters | Every required query parameter is present; types match the schema (string/integer/etc.). |
| Path parameters | Path parameter types match the spec's `parameters[in=path]` schema. |

The deeper checks (everything below "response status" in the table) are
performed via the Atlassian `swagger-request-validator-core` library, which
adapts each `Contract` into a synthetic HTTP request/response and validates
both against the OpenAPI document. Path/method/status checks remain in the
existing `OpenApiContractsVerifier` code path.

Behaviour on drift is controlled by the system property
`scc.oa3.converter.drift` (default `fail`):

| Value  | Behaviour |
|--------|-----------|
| `fail` | A `OpenApiContractDriftException` is thrown; no contracts are returned. (default) |
| `warn` | Violations are logged at WARN level with the rendered report. Contracts are still returned. |
| `off`  | Verification is skipped entirely. |

The OpenAPI document is parsed once and reused for verification — no double
parse cost.

## API

```java
// SPI / direct usage — no API change for callers
Collection<Contract> contracts = new OpenApiContractConverter().convertFrom(openApiFile);
// In `fail` mode (default), throws OpenApiContractDriftException if drift is detected.
// In `warn` mode, logs WARN with full violation report and returns contracts.
```

```java
// New exception (public, runtime)
public class OpenApiContractDriftException extends RuntimeException {
    public OpenApiVerificationReport report();
}
```

System property:

- `scc.oa3.converter.drift` — `fail` (default), `warn`, or `off`.

## Business Rules

1. Drift verification runs only when `convertFrom` successfully produced at
   least one contract from the OpenAPI source. If conversion itself failed,
   drift is not checked (the conversion error is the primary failure).
2. The OpenAPI document used for verification is the same in-memory `OpenAPI`
   model produced during conversion — parsed exactly once. The converter never
   re-reads or re-parses the spec file for drift purposes.
13. Remote `$ref` resolution at parse time is disabled. A spec referencing
    `https://...` URLs is parsed without those references being followed.
3. Verification is performed via the existing `OpenApiContractsVerifier`
   in-memory mode; the path/method/status drift rules from
   [001-contract-validation.md](001-contract-validation.md) apply unchanged.
   In addition, schema-level checks (bodies, headers, query/path params,
   content types) run against the same OpenAPI document.
9. Schema-level checks are skipped for a contract that already failed the
   path/method/status checks — there is no operation in the spec to validate
   against, so reporting schema noise on top would be misleading.
10. Contracts that have no request/response body do not trigger body-schema
    violations even if the spec declares a schema, unless the spec marks the
    body as required.
11. Path parameter values inside the contract URL are not type-checked against
    `parameters[in=path].schema` when the value is a regex matcher (the regex
    is the type contract). Concrete literal values are type-checked. The
    suppression only applies to messages whose `MessageContext.getParameter()`
    is `in: path` — query-parameter, header-parameter, body, and content-type
    violations are *never* suppressed by this rule.
12. Two-sided `DslProperty` values (`value(producer(...), consumer(...))`) are
    validated using the **server** value — that is what the stub will emit.
4. In `warn` mode, drift never prevents contracts from being returned.
5. In `fail` mode, drift suppresses the contract collection entirely; the
   `OpenApiContractDriftException` carries the full report.
6. In `off` mode, no verification runs and no log is emitted.
7. The system property is read on every `convertFrom` call (no caching), so
   tests and runtime can toggle it without reinitialising the converter.
8. Property values are case-insensitive and trimmed; unknown values fall back
   to `fail` and emit a one-line WARN explaining the fallback.

## Acceptance Criteria

### Drift-Free Conversion Returns Contracts

**Given** an OpenAPI spec that converts cleanly into contracts
**When** `convertFrom` is called with the default mode
**Then** contracts are returned and no drift exception or WARN is emitted

### Drift in Default (Fail) Mode Throws

**Given** an OpenAPI spec that produces a contract whose method/path/status
does not appear in the spec (a converter bug or partial conversion)
**When** `convertFrom` is called with the default mode
**Then** `OpenApiContractDriftException` is thrown, its `report()` reports
the violations, and no contracts are returned

### Drift in Warn Mode Logs and Returns Contracts

**Given** the same drifted conversion as above
**When** `convertFrom` is called with mode `warn`
**Then** contracts are still returned, and a WARN log line contains the
rendered violation report

### Off Mode Skips Verification

**Given** any OpenAPI input
**When** `convertFrom` is called with mode `off`
**Then** no verifier is invoked and no drift log is emitted

### Conversion Failure Suppresses Drift Check

**Given** an OpenAPI file that fails to parse
**When** `convertFrom` is called
**Then** the existing error path is taken (empty collection returned, error
logged) and no drift verification or `OpenApiContractDriftException` is raised

### Unknown Mode Falls Back to Fail

**Given** `scc.oa3.converter.drift=banana`
**When** `convertFrom` runs against drifted output
**Then** behaviour matches `fail` mode and a single WARN explains the fallback

### Request Body Schema Drift

**Given** an OpenAPI spec declaring `POST /orders` with `requestBody` requiring
a JSON object with a required `customerId` field of type `string`
**When** a contract submits `POST /orders` with body `{"customer_id": 42}`
**Then** the report contains a violation pointing at the missing required
field `customerId` and the type mismatch on `customerId`/`customer_id`

### Response Body Schema Drift

**Given** an OpenAPI spec declaring `GET /orders/{id}` returning `200` with a
schema requiring `id` and `total` (number)
**When** a contract responds with `{"id": "abc"}` (missing `total`)
**Then** the report contains a violation for the missing required field

### Required Request Header Missing

**Given** an OpenAPI spec declaring a required `X-Tenant-Id` header on
`GET /orders`
**When** a contract for `GET /orders` does not declare `X-Tenant-Id`
**Then** the report contains a violation naming the missing header

### Required Response Header Missing

**Given** an OpenAPI spec declaring `Location` as a required response header
on `POST /orders` 201
**When** a contract for `POST /orders` 201 omits `Location` from the response
**Then** the report contains a violation naming the missing response header

### Required Query Parameter Missing

**Given** an OpenAPI spec declaring a required query parameter `since` on
`GET /events`
**When** a contract calls `GET /events` with no `since` parameter
**Then** the report contains a violation naming the missing query parameter

### Query Parameter Type Mismatch

**Given** an OpenAPI spec declaring `limit` as integer on `GET /events`
**When** a contract calls `GET /events?limit=banana`
**Then** the report contains a violation indicating type mismatch

### Path Parameter Type Mismatch

**Given** an OpenAPI spec declaring `id` as integer on `GET /orders/{id}`
**When** a contract calls `GET /orders/abc` with a literal non-integer
**Then** the report contains a violation indicating path parameter type
mismatch

### Content Type Drift

**Given** an OpenAPI spec declaring `application/json` only for the request
body of `POST /orders`
**When** a contract sets `Content-Type: application/xml`
**Then** the report contains a violation indicating an unsupported request
content type

### Server Value Used For DslProperty

**Given** an OpenAPI spec requiring an integer in the response body
**When** a contract uses `value(producer(123), consumer("abc"))` for that field
**Then** validation passes — the producer (server) value is what the stub
serves, and it matches the schema

### Remote $ref Refused At Parse Time

**Given** an OpenAPI spec containing `$ref: 'https://attacker.invalid/x'`
**When** `convertFrom` (or `verifyInMemory(String, ...)`) parses it
**Then** the parser does not open an outbound HTTP connection — the spec is
parsed with remote-ref resolution disabled, so the build JVM is not used as
an SSRF proxy

## Error Cases

| Scenario | Behaviour |
|----------|-----------|
| OpenAPI file unparseable | Parse-error messages from the parser are surfaced as violations (rather than silently dropped); the misleading "no paths" violation only appears when the file is genuinely empty of paths. |
| Verifier itself throws | Logged at ERROR; **wrapped** and rethrown as `OpenApiContractDriftException` carrying a synthetic single-violation report (fail-fast). The original cause is preserved as the exception's cause. |
| Drift detected, mode=fail | `OpenApiContractDriftException` with full report |
| Drift detected, mode=warn | WARN log, contracts returned |

## Out of Scope

- Configuration via annotation — covered by feature 003 for the JUnit path.
- Validation of SCC matcher DSLs (regex/predicate matchers) beyond what the
  server value can express. A regex matcher on a body field passes whatever
  string satisfies the regex; the schema check only looks at concrete server
  values.
- Mutual TLS, auth tokens, OAuth scopes — these are runtime concerns, not
  contract drift.
