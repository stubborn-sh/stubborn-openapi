# Feature 3: JUnit 5 Extension for Automated Validation

## What

A JUnit 5 extension (`OpenApiContractsVerifierExtension`) and annotation
(`@VerifyContractsAgainstOpenApi`) that automatically validates SCC contracts against an
OpenAPI specification before test execution, failing the test class if any violations are
found.

## Why

Manual validation of contracts against OpenAPI specs is error-prone and easy to forget.
Integrating validation into the test lifecycle ensures that:
- Contract drift is caught on every test run
- CI pipelines fail fast when contracts diverge from the API spec
- Developers get immediate feedback without running a separate tool

## How (High Level)

`@VerifyContractsAgainstOpenApi` is a type-level annotation that triggers
`OpenApiContractsVerifierExtension` via `@ExtendWith`. The extension implements
`BeforeAllCallback` and runs validation once before all tests in the annotated class. It
resolves the OpenAPI spec path and contracts directory from the annotation attributes,
falling back to system properties (`scc.oa3.spec` and `scc.contracts.dir`). If violations
are found, it throws an `AssertionError` with the rendered report.

## API

```java
@VerifyContractsAgainstOpenApi(
    openApiSpec = "src/main/resources/openapi.yaml",
    contractsDir = "src/test/resources/contracts"
)
class MyContractTests {
    // tests run only if contracts match the OpenAPI spec
}
```

### Annotation Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `openApiSpec` | `String` | `""` | Path to the OpenAPI specification file |
| `contractsDir` | `String` | `""` | Path to the contracts directory |

### System Property Fallbacks

| Property | Description |
|----------|-------------|
| `scc.oa3.spec` | Fallback for OpenAPI spec path when annotation attribute is blank |
| `scc.contracts.dir` | Fallback for contracts directory when annotation attribute is blank |

## Business Rules

1. The annotation is type-level only (`@Target(ElementType.TYPE)`)
2. The annotation is retained at runtime (`@Retention(RetentionPolicy.RUNTIME)`)
3. Annotation attributes take precedence over system properties
4. If an annotation attribute is blank (empty string), the corresponding system property is used
5. If both annotation attribute and system property are blank, an `IllegalStateException` is thrown with a message listing both configuration options
6. Validation runs once before all tests in the class (`BeforeAllCallback`)
7. If the verification report has violations, an `AssertionError` is thrown with the rendered report
8. If the verification report has no violations, tests proceed normally

## Acceptance Criteria

### Validation Passes -- Tests Run

**Given** a test class annotated with `@VerifyContractsAgainstOpenApi` pointing to valid paths
**And** all contracts match the OpenAPI specification
**When** the test class is executed
**Then** `beforeAll` completes without error and all tests in the class run

### Validation Fails -- Tests Blocked

**Given** a test class annotated with `@VerifyContractsAgainstOpenApi`
**And** one contract references an endpoint not in the OpenAPI spec
**When** the test class is executed
**Then** an `AssertionError` is thrown before any test method runs
**And** the error message contains the rendered violation report

### System Property Fallback

**Given** a test class annotated with `@VerifyContractsAgainstOpenApi` with blank attributes
**And** system properties `scc.oa3.spec` and `scc.contracts.dir` are set
**When** the test class is executed
**Then** the extension uses the system property values for validation

### Missing Configuration

**Given** a test class annotated with `@VerifyContractsAgainstOpenApi` with blank attributes
**And** no system properties are set
**When** the test class is executed
**Then** an `IllegalStateException` is thrown with a message about missing configuration

### Annotation Overrides System Properties

**Given** system properties `scc.oa3.spec` and `scc.contracts.dir` are set
**And** the annotation provides different paths
**When** the test class is executed
**Then** the annotation paths are used, not the system property values

## Error Cases

| Scenario | Exception | Message |
|----------|-----------|---------|
| Both annotation and system properties blank | `IllegalStateException` | "OpenAPI spec and contracts directory must be configured via @VerifyContractsAgainstOpenApi or system properties" |
| Contracts violate OpenAPI spec | `AssertionError` | Rendered violation report |
| OpenAPI spec file missing | `AssertionError` | Report containing "OpenAPI specification file does not exist" |
| Contracts directory missing | `AssertionError` | Report containing "Contracts directory does not exist" |
