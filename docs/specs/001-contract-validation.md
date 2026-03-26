# Feature 1: Contract Validation Against OpenAPI

## What

Validates Spring Cloud Contract DSL files against an OpenAPI 3.x specification,
detecting missing endpoints, invalid HTTP methods, and undefined response status codes.

## Why

Contracts that reference endpoints or response codes not defined in the OpenAPI spec
indicate drift between the API design and the contract tests. Catching these mismatches
early prevents:
- Contracts that test nonexistent endpoints
- Status codes that the API never returns
- HTTP methods not supported by a given path
- Silent breakage when the OpenAPI spec evolves but contracts are not updated

## How (High Level)

The `OpenApiContractsVerifier` parses an OpenAPI 3.x specification into an indexed
structure (`OpenApiSpecIndex`) mapping paths to methods to response status codes. It then
loads all contract files from a directory using SCC's `ContractConverter` SPI (supporting
YAML, Groovy, Java formats) and validates each contract's request method + URL path +
response status against the index. Path matching handles OpenAPI path templates
(`/users/{id}`) and contract-side templates via segment-by-segment comparison in
`OpenApiPathMatcher`. Wildcard response codes (`2XX`, `default`) are matched against
concrete status codes.

An in-memory variant (`verifyInMemory`) accepts OpenAPI and contract content as strings,
enabling runtime validation without files on disk.

## API

```java
// File-based validation
OpenApiVerificationReport report = new OpenApiContractsVerifier()
    .verify(Path.of("openapi.yaml"), Path.of("contracts/"));

// In-memory validation
OpenApiVerificationReport report = new OpenApiContractsVerifier()
    .verifyInMemory(openApiYamlString, contractYamlString);

// Check results
report.hasViolations();          // boolean
report.violations();             // List<OpenApiContractViolation>
report.render();                 // human-readable report string
```

### OpenApiContractViolation

| Field | Type | Description |
|-------|------|-------------|
| `sourcePath` | `Path` | File path of the contract (or `in-memory` for string-based) |
| `contractName` | `String` | Name of the contract or file#index |
| `message` | `String` | Human-readable violation description |

## Business Rules

1. Contracts marked `ignored` or `inProgress` are skipped
2. Each contract must define a request method, request URL, and response status; otherwise it is a violation
3. The contract's URL path is matched against OpenAPI paths using segment-by-segment comparison
4. Path template segments (`{paramName}`) in either the spec or the contract match any value
5. Query parameters are stripped before path matching
6. Full URIs (containing `://`) are reduced to their path component before matching
7. Trailing slashes are normalized (removed) before comparison
8. If no OpenAPI path matches the contract path, a violation is reported
9. If a matching path exists but not the HTTP method, a violation is reported
10. If path and method match but the response status code is not defined, a violation is reported
11. OpenAPI `default` responses match any status code
12. OpenAPI wildcard status ranges (`2XX`, `4XX`) match any status starting with that digit
13. Contract files are discovered recursively from the contracts directory
14. File formats are detected via SCC's `ContractConverter` SPI (registered through `spring.factories`)

## Acceptance Criteria

### Valid Contract Passes

**Given** an OpenAPI spec defining `GET /users/{id}` with response `200`
**When** I validate a contract with `GET /users/123` returning status `200`
**Then** the report has no violations

### Missing Endpoint

**Given** an OpenAPI spec defining only `GET /users`
**When** I validate a contract with `GET /orders/456` returning status `200`
**Then** the report contains a violation "No OpenAPI path matches contract request GET /orders/456"

### Invalid HTTP Method

**Given** an OpenAPI spec defining `GET /users/{id}` but not `DELETE /users/{id}`
**When** I validate a contract with `DELETE /users/123` returning status `204`
**Then** the report contains a violation "No OpenAPI operation matches contract request DELETE /users/123"

### Invalid Status Code

**Given** an OpenAPI spec defining `GET /users/{id}` with responses `200` and `404`
**When** I validate a contract with `GET /users/123` returning status `500`
**Then** the report contains a violation "No OpenAPI response status 500 for contract request GET /users/123"

### Wildcard Status Code Matches

**Given** an OpenAPI spec defining `POST /users` with response `2XX`
**When** I validate a contract with `POST /users` returning status `201`
**Then** the report has no violations

### Default Response Matches Any Status

**Given** an OpenAPI spec defining `GET /health` with response `default`
**When** I validate a contract with `GET /health` returning status `503`
**Then** the report has no violations

### Ignored Contracts Are Skipped

**Given** a contract marked as `ignored: true`
**When** I validate it against any OpenAPI spec
**Then** no violation is reported for that contract

### In-Progress Contracts Are Skipped

**Given** a contract marked as `inProgress: true`
**When** I validate it against any OpenAPI spec
**Then** no violation is reported for that contract

### In-Memory Validation

**Given** an OpenAPI spec as a YAML string and a contract as a YAML string
**When** I call `verifyInMemory` with both strings
**Then** the report reflects violations (or lack thereof) based on the content

### Incomplete Contract

**Given** a contract YAML that defines a request method but no URL
**When** I validate it against an OpenAPI spec
**Then** the report contains a violation "Contract must define request method, request URL, and response status"

### Recursive Directory Scanning

**Given** contracts in nested subdirectories under the contracts directory
**When** I validate against an OpenAPI spec
**Then** all contracts in all subdirectories are validated

## Error Cases

| Scenario | Behavior |
|----------|----------|
| OpenAPI spec file does not exist | Violation: "OpenAPI specification file does not exist" |
| Contracts directory does not exist | Violation: "Contracts directory does not exist" |
| OpenAPI spec contains no paths | Violation: "OpenAPI specification contains no paths" |
| OpenAPI spec is unparseable | Violation: "Failed to read OpenAPI specification: ..." |
| Contract file cannot be parsed | Violation: "Failed to parse contract file: ..." |
| Contract YAML invalid (in-memory) | Violation: "Failed to parse contract YAML: ..." |
| Contract missing method/URL/status | Violation: "Contract must define request method, request URL, and response status" |
