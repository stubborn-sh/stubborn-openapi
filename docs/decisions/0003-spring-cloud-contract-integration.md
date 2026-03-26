# ADR 0003: Leveraging SCC's ContractConverter SPI and YamlToContracts

## Status

Accepted

## Context

The library needs to:
1. Load contracts in multiple formats (YAML, Groovy DSL, Java DSL, OpenAPI with
   `x-contracts`) for validation
2. Convert OpenAPI specs with `x-contracts` into SCC Contract objects
3. Integrate seamlessly with the existing SCC ecosystem so that users do not need to
   change their contract authoring workflow

Spring Cloud Contract provides two key extension points:
- `ContractConverter` SPI -- an interface for converting between external formats and SCC's
  `Contract` model, discovered via `spring.factories`
- `YamlToContracts` -- converts SCC YAML DSL into `Contract` objects

## Decision

**For contract loading during validation**: Use `SpringFactoriesLoader` to discover all
registered `ContractConverter` implementations. For each contract file, iterate through
converters and use the first one whose `isAccepted(File)` returns `true`. This approach
automatically supports all formats that SCC supports, including any third-party converters
on the classpath.

**For OpenAPI-to-SCC conversion**: Implement `ContractConverter<Collection<PathItem>>` as
`OpenApiContractConverter`, registered in `spring.factories`. The converter uses
`Oa3ToScc` to transform OpenAPI operations with `x-contracts` extensions into
`YamlContract` objects, then feeds those through `TempYamlToContracts` (which delegates to
SCC's `YamlToContracts`) to produce final `Contract` instances. This two-step approach
(OpenAPI -> YamlContract -> Contract) reuses SCC's mature YAML-to-Contract logic rather
than reimplementing it.

**For in-memory validation**: Use `YamlContractConverter.INSTANCE` directly, writing the
contract string to a temporary file since `YamlToContracts` requires a `File` input.

## Consequences

- **Positive**: Automatic support for all SCC contract formats without writing custom parsers
- **Positive**: Third-party `ContractConverter` implementations are picked up automatically
- **Positive**: The OpenAPI converter integrates transparently with SCC plugins (stub generation, verification)
- **Positive**: Reusing `YamlToContracts` ensures contract semantics are identical to native SCC YAML processing
- **Positive**: The converter is discoverable by any SCC tool, not just this validator
- **Negative**: The temporary file approach in `TempYamlToContracts` and `verifyInMemory` is a workaround for `YamlToContracts` requiring a `File`
- **Negative**: `SpringFactoriesLoader` loads all converters eagerly, including ones that may not be needed
- **Negative**: Converter ordering in `spring.factories` matters -- `OpenApiContractConverter` must be listed before `YamlContractConverter` to prevent OpenAPI files from being rejected as invalid SCC YAML
