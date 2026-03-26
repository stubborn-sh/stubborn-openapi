# ADR 0002: Swagger Parser v3 for OpenAPI Parsing

## Status

Accepted

## Context

The library needs to parse OpenAPI 3.x specifications (both YAML and JSON) to extract
paths, operations, parameters, request bodies, responses, and vendor extensions
(`x-contracts`). Several options exist:

1. **Swagger Parser v3** (`io.swagger.parser.v3:swagger-parser`) -- the de facto standard
   OpenAPI parser in the Java ecosystem, maintained by the Swagger/SmartBear team
2. **OpenAPI4J** -- an alternative parser, less widely adopted
3. **Custom YAML/JSON parsing** -- parse the spec manually with Jackson/SnakeYAML
4. **Spring's OpenAPI support** -- not available as a standalone parser

## Decision

Use **Swagger Parser v3** (version 2.1.39) for all OpenAPI parsing.

The parser is used in two places:
- `Oa3Parser` (converter package) -- `new OpenAPIV3Parser().read(file.getPath())` for
  file-based parsing during contract conversion
- `OpenApiContractsVerifier` (validation package) -- `new OpenAPIV3Parser().read(path)`
  for file-based validation, and `new OpenAPIV3Parser().readContents(string).getOpenAPI()`
  for in-memory validation

## Consequences

- **Positive**: Industry-standard library with broad community adoption and proven reliability
- **Positive**: Handles both YAML and JSON transparently
- **Positive**: Full OpenAPI 3.0 and 3.1 support including `$ref` resolution, schema validation, and extensions
- **Positive**: Provides typed access to all OpenAPI model elements (PathItem, Operation, Parameter, etc.)
- **Positive**: Well-maintained with regular releases
- **Negative**: Brings transitive dependencies (Jackson, SnakeYAML, swagger-core, swagger-models)
- **Negative**: Parser validates the spec during parsing, which may reject specs with minor issues that would not affect contract validation
