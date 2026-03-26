# ADR 0001: Standalone Library -- No Stubborn Dependency Required

## Status

Accepted

## Context

The Stubborn OpenAPI Validator needs to validate SCC contracts against OpenAPI specs.
Stubborn is a contract broker, but many teams use SCC without a broker, relying on Git,
Maven repositories, or local file systems for contract storage. Requiring a Stubborn
dependency would limit adoption to only Stubborn users and force an unnecessary runtime
dependency on a server-side component.

The library also needs to work in diverse environments: inside JUnit tests, in CI
pipelines via CLI, and as part of Maven/Gradle builds.

## Decision

Ship the OpenAPI validator as a **standalone library** with zero Stubborn dependencies.
The only required dependencies are:

- `spring-cloud-contract-verifier` (for contract parsing and the `ContractConverter` SPI)
- `spring-cloud-contract-spec-java` (for Java DSL support)
- `swagger-parser` v3 (for OpenAPI 3.x parsing)
- `junit-jupiter-api` (for the JUnit 5 extension annotation)

The Maven coordinates are `sh.stubborn:spring-cloud-contract-openapi-validator`,
establishing the Stubborn brand while making it clear this works with any SCC project.

## Consequences

- **Positive**: Any team using Spring Cloud Contract can adopt the validator without buying into the full Stubborn ecosystem
- **Positive**: The library can be added to existing SCC projects with a single Maven/Gradle dependency
- **Positive**: No network calls, no server configuration -- pure local validation
- **Positive**: Smaller dependency footprint, faster startup
- **Negative**: Cannot leverage Stubborn-specific features (contract storage, verification history, can-i-deploy) from within this library
- **Negative**: The library must handle all contract format parsing independently rather than delegating to a Stubborn API
