# Feature 5: CLI Validation Tool

## What

A command-line interface (`OpenApiContractsVerifierMain`) for standalone validation of SCC
contracts against an OpenAPI specification, runnable as a Java main class without
requiring a test framework or build plugin.

## Why

Not all validation workflows happen inside test suites. Teams need a way to:
- Validate contracts in CI pipelines without running the full test suite
- Integrate validation into pre-commit hooks or code review automation
- Perform ad-hoc validation during development
- Run validation from build scripts or Makefiles

## How (High Level)

`OpenApiContractsVerifierMain` is a simple `main` class that accepts two positional
arguments: the path to the OpenAPI spec and the path to the contracts directory. It
delegates to `OpenApiContractsVerifier.verify()` and reports results to stdout (success)
or stderr (violations). Exit codes communicate the result to the calling process.

## API

```
Usage: <openapi-spec-path> <contracts-directory>
```

### Arguments

| Position | Name | Required | Description |
|----------|------|----------|-------------|
| 1 | `openapi-spec-path` | Yes | Path to the OpenAPI 3.x specification file (YAML or JSON) |
| 2 | `contracts-directory` | Yes | Path to the directory containing SCC contract files |

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All contracts match the OpenAPI specification |
| 1 | One or more violations found |
| 2 | Invalid usage (wrong number of arguments) |

### Output

| Stream | Condition | Content |
|--------|-----------|---------|
| stdout | No violations | Rendered success message |
| stderr | Violations found | Rendered violation report |
| stderr | Invalid usage | Usage message |

## Business Rules

1. Exactly two arguments are required; any other count prints usage to stderr and exits with code 2
2. The first argument is the OpenAPI spec file path
3. The second argument is the contracts directory path
4. Validation is delegated entirely to `OpenApiContractsVerifier.verify()`
5. If the report has violations, the rendered report is printed to stderr and the process exits with code 1
6. If the report has no violations, the rendered report (success message) is printed to stdout and the process exits with code 0

## Acceptance Criteria

### Successful Validation

**Given** a valid OpenAPI spec and a contracts directory where all contracts match
**When** I run `java -cp ... OpenApiContractsVerifierMain openapi.yaml contracts/`
**Then** stdout contains the success message
**And** the exit code is 0

### Validation With Violations

**Given** a contracts directory with a contract referencing a nonexistent endpoint
**When** I run `java -cp ... OpenApiContractsVerifierMain openapi.yaml contracts/`
**Then** stderr contains the violation report
**And** the exit code is 1

### No Arguments

**Given** no arguments
**When** I run `java -cp ... OpenApiContractsVerifierMain`
**Then** stderr contains "Usage: <openapi-spec-path> <contracts-directory>"
**And** the exit code is 2

### One Argument

**Given** only one argument
**When** I run `java -cp ... OpenApiContractsVerifierMain openapi.yaml`
**Then** stderr contains the usage message
**And** the exit code is 2

### Nonexistent Spec File

**Given** an OpenAPI spec path that does not exist
**When** I run the CLI
**Then** stderr contains the violation report with "OpenAPI specification file does not exist"
**And** the exit code is 1

### Nonexistent Contracts Directory

**Given** a contracts directory path that does not exist
**When** I run the CLI
**Then** stderr contains the violation report with "Contracts directory does not exist"
**And** the exit code is 1

## Error Cases

| Scenario | Exit Code | Output |
|----------|-----------|--------|
| Wrong argument count | 2 | Usage message to stderr |
| Spec file missing | 1 | Violation report to stderr |
| Contracts dir missing | 1 | Violation report to stderr |
| Unparseable spec | 1 | Violation report to stderr |
| Contract violations found | 1 | Violation report to stderr |
