# Stubborn OpenAPI Validator

Validates [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract) DSL files against OpenAPI specifications.

**No Stubborn dependency required** -- use this standalone with any Spring Cloud Contract project.

## Features

- Validate SCC contracts (YAML, Groovy, Java) against an OpenAPI spec — paths, methods, statuses, **bodies (JSON Schema), headers, query/path params, content types**
- Convert OpenAPI 3.x specs to SCC contract DSL with **inline drift detection** — every contract produced is verified against the spec it came from
- Hardened OpenAPI parsing — remote `$ref` resolution disabled (no SSRF surface)
- JUnit 5 extension for automated validation in tests
- Drift behaviour configurable via `-Dscc.oa3.converter.drift=fail|warn|off` (default `fail`)

## Usage

Add the dependency:

```xml
<dependency>
    <groupId>sh.stubborn</groupId>
    <artifactId>spring-cloud-contract-openapi-validator</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

Use the JUnit 5 extension:

```java
@ExtendWith(OpenApiContractsVerifierExtension.class)
@VerifyContractsAgainstOpenApi(openApiSpec = "openapi/my-service.yml")
class ContractValidationTest {
    // Contracts in src/test/resources/contracts/ are validated automatically
}
```

## Links

- [Stubborn](https://stubborn.sh) -- contract governance platform
- [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract)
