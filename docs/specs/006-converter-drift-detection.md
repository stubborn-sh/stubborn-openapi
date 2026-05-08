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

`OpenApiContractConverter.convertFrom(File)` already parses the OpenAPI spec via
`Oa3Parser` and converts it into a stream of contract YAML strings, then into
`Contract` objects. After conversion, the converter runs
`OpenApiContractsVerifier.verifyInMemory(...)` against the original OpenAPI
content for every produced contract, collecting violations into a single
`OpenApiVerificationReport`.

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
2. The OpenAPI document used for verification is the same document parsed for
   conversion — parsed exactly once.
3. Verification is performed via the existing `OpenApiContractsVerifier`
   in-memory mode; the path/method/status drift rules from
   [001-contract-validation.md](001-contract-validation.md) apply unchanged.
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

## Error Cases

| Scenario | Behaviour |
|----------|-----------|
| OpenAPI file unparseable | Existing error path; no drift check |
| Verifier itself throws | Logged at ERROR; rethrown as `OpenApiContractDriftException` (fail-fast) |
| Drift detected, mode=fail | `OpenApiContractDriftException` with full report |
| Drift detected, mode=warn | WARN log, contracts returned |

## Out of Scope

- Schema-level drift (request/response body, headers, query params). Tracked
  separately; this feature only wires the existing path/method/status verifier
  into the conversion path.
- Configuration via annotation — covered by feature 003 for the JUnit path.
