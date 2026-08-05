# Stubborn OpenAPI Validator

Validates [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract) DSL files against OpenAPI specifications.

**No Stubborn dependency required** -- use this standalone with any Spring Cloud Contract project.

## Features

- Validate SCC contracts (YAML, Groovy, Java) against an OpenAPI spec
- Convert OpenAPI 3.x specs to SCC contract DSL
- JUnit 5 extension for automated validation in tests
- Detect missing endpoints, invalid status codes, schema mismatches

## Usage

Add the dependency:

```xml
<dependency>
    <groupId>sh.stubborn</groupId>
    <artifactId>stubborn-openapi-validator</artifactId>
    <version>0.2.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

Snapshots are published to Maven Central. To resolve them, add:

```xml
<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <snapshots><enabled>true</enabled></snapshots>
        <releases><enabled>false</enabled></releases>
    </repository>
</repositories>
```

Use the JUnit 5 extension. The annotation is meta-annotated with
`@ExtendWith`, so applying it registers the extension. Both the OpenAPI spec
and the contracts directory are required:

```java
@VerifyContractsAgainstOpenApi(
        openApiSpec = "src/test/resources/openapi/my-service.yml",
        contractsDir = "src/test/resources/contracts")
class ContractValidationTest {
    // Every contract under contractsDir is validated against the OpenAPI spec
}
```

## Links

- [Stubborn](https://stubborn.sh) -- contract governance platform
- [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract)
