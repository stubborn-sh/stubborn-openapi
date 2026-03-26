# Feature 4: Multi-Format Support

## What

Supports loading contracts in all formats recognized by Spring Cloud Contract (YAML,
Groovy DSL, Java DSL) and OpenAPI specifications in both YAML and JSON formats.

## Why

Teams use different contract authoring styles depending on their preferences and
toolchain. The validator must work with all of them without requiring format conversion.
Similarly, OpenAPI specs may be authored in YAML or JSON depending on tooling and
organizational standards.

## How (High Level)

Contract loading leverages SCC's `ContractConverter` SPI. All registered `ContractConverter`
implementations are discovered via `SpringFactoriesLoader` from `spring.factories`. For
each file found recursively in the contracts directory, the loader iterates through
converters and uses the first one that accepts the file (`isAccepted`). The registered
converters include:

- `YamlContractConverter` -- handles `.yml` and `.yaml` SCC contract files
- `ContractVerifierDslConverter` -- handles `.groovy` Groovy DSL contracts
- `OpenApiContractConverter` -- handles OpenAPI `.yml`, `.yaml`, `.json` files with `x-contracts`

Java DSL contracts (`.java` files) are supported through SCC's built-in Java contract
support when available on the classpath.

For OpenAPI spec parsing, Swagger Parser v3 (`OpenAPIV3Parser`) handles both YAML and
JSON input transparently, whether from file paths or in-memory strings.

## API

No additional API beyond what is described in Feature 1 (Contract Validation) and Feature
2 (OpenAPI to SCC Conversion). Format support is transparent to the caller.

### Supported Contract Formats

| Format | Extension | Converter |
|--------|-----------|-----------|
| SCC YAML | `.yml`, `.yaml` | `YamlContractConverter` |
| Groovy DSL | `.groovy` | `ContractVerifierDslConverter` |
| Java DSL | `.java` | SCC built-in (when on classpath) |
| OpenAPI 3.x | `.yml`, `.yaml`, `.json` | `OpenApiContractConverter` |

### Supported OpenAPI Spec Formats

| Format | Extension | Parser |
|--------|-----------|--------|
| YAML | `.yml`, `.yaml` | `OpenAPIV3Parser` |
| JSON | `.json` | `OpenAPIV3Parser` |

## Business Rules

1. Contract format detection is delegated to SCC's `ContractConverter.isAccepted(File)` method
2. Converters are loaded once via `SpringFactoriesLoader` and cached in a static field
3. The first converter that accepts a file is used; no fallback to other converters
4. Files not accepted by any converter are silently skipped (not reported as violations)
5. Files accepted by a converter but producing an empty contract list are silently skipped (handles cases like Java contracts on unsupported JDK versions)
6. Files accepted by a converter that throw an exception during parsing produce a violation: "Failed to parse contract file: ..."
7. OpenAPI spec files are identified by the `OpenApiContractConverter` checking the first non-empty, non-comment line for `openapi`, `swagger`, `"openapi"`, `"swagger"`, or `{` (JSON start)
8. OpenAPI specs are distinguished from SCC YAML contracts by the file content heuristic, not by file extension alone
9. In-memory validation (`verifyInMemory`) only supports YAML contract format, parsed via `YamlContractConverter`
10. The `spring.factories` file registers the converter order: `OpenApiContractConverter`, `ContractVerifierDslConverter`, `YamlContractConverter`

## Acceptance Criteria

### YAML Contract Validation

**Given** a contracts directory containing `.yml` SCC contract files
**When** I validate against an OpenAPI spec
**Then** all YAML contracts are loaded and validated

### Groovy DSL Contract Validation

**Given** a contracts directory containing `.groovy` SCC contract files
**When** I validate against an OpenAPI spec
**Then** all Groovy contracts are loaded and validated

### OpenAPI Files in Contracts Directory

**Given** a contracts directory containing an OpenAPI `.yaml` file with `x-contracts`
**When** I validate against a separate OpenAPI spec
**Then** the OpenAPI file is converted to contracts via `OpenApiContractConverter` and validated

### Mixed Formats in Same Directory

**Given** a contracts directory containing `.yml`, `.groovy`, and `.yaml` (OpenAPI) files
**When** I validate against an OpenAPI spec
**Then** each file is loaded by the appropriate converter and all contracts are validated

### JSON OpenAPI Spec

**Given** an OpenAPI specification in JSON format
**When** I validate contracts against it
**Then** the JSON spec is parsed correctly and validation proceeds

### Unknown File Format Skipped

**Given** a contracts directory containing a `.txt` file alongside contract files
**When** I validate against an OpenAPI spec
**Then** the `.txt` file is silently ignored

### Corrupt Contract File

**Given** a contracts directory containing a `.yml` file with invalid YAML syntax
**When** I validate against an OpenAPI spec
**Then** a violation is reported for that file: "Failed to parse contract file: ..."
**And** other valid contracts are still validated

## Error Cases

| Scenario | Behavior |
|----------|----------|
| Unrecognized file extension | Silently skipped |
| Corrupt contract file | Violation reported, other files still processed |
| Empty contract list from converter | Silently skipped |
| Failed directory scan | Violation: "Failed to scan contracts directory: ..." |
