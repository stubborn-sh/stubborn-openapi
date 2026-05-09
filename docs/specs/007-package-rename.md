# Feature 7: Package rename to `sh.stubborn.openapi.*`

## What

The library's Java code lives under packages owned by Spring Cloud Contract
(`org.springframework.cloud.contract.verifier.*`). This is misleading — the
artifact is `sh.stubborn:stubborn-openapi-validator`, not a Spring project.
Rename every package to `sh.stubborn.openapi.*`, preserving the existing
nesting (validation vs converter), and delete the old packages outright.

## Why

- Public API users importing classes will see the correct organisation.
- The `org.springframework.cloud.contract` namespace is reserved for upstream
  Spring Cloud Contract; squatting in it is hostile.
- Avoids future collisions if SCC adds classes with the same simple names.

## How (High Level)

| From | To |
|------|----|
| `org.springframework.cloud.contract.verifier.openapivalidation` | `sh.stubborn.openapi.validation` |
| `org.springframework.cloud.contract.verifier.converter` | `sh.stubborn.openapi.converter` |

- `git mv` for every file so history is preserved.
- All package declarations + intra-project imports rewritten.
- `META-INF/spring.factories` test fixture stops listing our converter — we
  do **not** register `OpenApiContractConverter` via the SCC SPI. Code paths
  that previously depended on SPI lookup of our converter are reworked to
  instantiate it directly.
- Old packages are deleted outright; no compatibility shims, no deprecated
  re-exports.

## Business Rules

1. Every `package org.springframework.cloud.contract.verifier.*;` declaration
   in `src/main` and `src/test` is replaced with the matching
   `sh.stubborn.openapi.*` equivalent.
2. Every import referencing the old packages is rewritten.
3. `package-info.java` files keep their `@NullMarked` / `@NullUnmarked`
   annotations.
4. `OpenApiContractConverter` is **not** registered in
   `META-INF/spring.factories`. SCC's own converters
   (`ContractVerifierDslConverter`, `YamlContractConverter`) remain registered
   via SCC's own jars.
5. `OpenApiContractsVerifier`, when scanning a contracts directory, must still
   recognise OpenAPI YAML files. Since SPI lookup will no longer return our
   converter, the verifier instantiates `OpenApiContractConverter` directly
   alongside the SPI-loaded SCC converters.
6. No package directory under `org.springframework.cloud.contract.verifier`
   remains in the repository after the rename.
7. Documentation (`docs/`, `README.md`, spec internal links) is updated to
   reference the new paths.
8. Classes that previously referenced SCC-internal types (e.g. `YamlContract`)
   via implicit same-package access now require explicit imports of those
   SCC types. The SCC types themselves stay in their original packages —
   we are only moving our own classes.

## Acceptance Criteria

### Old Packages Are Gone

**Given** the rename has landed
**When** `find src -path '*/org/springframework/cloud/contract/verifier/*'` is
run
**Then** zero results are returned

### Tests Compile And Pass

**Given** the rename has landed
**When** `./mvnw verify` is run
**Then** the build succeeds with the same test count as before the rename

### SPI Registration Removed

**Given** the rename has landed
**When** `src/test/resources/META-INF/spring.factories` is read
**Then** no entry references
`sh.stubborn.openapi.converter.OpenApiContractConverter`

### Verifier Still Recognises OpenAPI Inputs

**Given** a contracts directory containing an OpenAPI YAML
**When** `OpenApiContractsVerifier.verify` is run
**Then** the OpenAPI file is converted into contracts and validated, even
without an SPI registration of our converter

## Out of Scope

- Maven group/artifact rename — already `sh.stubborn`; no change needed.
- Public API surface review beyond what the rename forces.
- New features. This PR is a pure rename.
