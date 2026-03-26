# Feature 2: OpenAPI to SCC Contract Conversion

## What

Converts OpenAPI 3.x specifications into Spring Cloud Contract YAML DSL, using
`x-contracts` vendor extensions embedded in the OpenAPI operations, parameters, request
bodies, and responses.

## Why

Teams that maintain an OpenAPI specification as the source of truth need a way to
generate contract tests from it without manually writing duplicate contract definitions.
The `x-contracts` extension mechanism lets teams co-locate contract examples alongside
their API definition, enabling:
- Single source of truth for API shape and contract examples
- Automatic generation of SCC contracts from the OpenAPI spec
- Seamless integration with the SCC plugin ecosystem (stub generation, verification)

## How (High Level)

`OpenApiContractConverter` implements SCC's `ContractConverter` SPI and is registered via
`spring.factories`. When SCC encounters a YAML/JSON file, the converter checks if it
looks like an OpenAPI spec (starts with `openapi`, `swagger`, or `{`). If accepted, it
parses the spec with Swagger Parser, then `Oa3ToScc` iterates over all operations that
contain `x-contracts` extensions. Each `x-contracts` entry produces one `YamlContract`
with request (method, path, headers, query parameters, cookies, body, multipart,
matchers) and response (status, headers, body, matchers) populated from the OpenAPI
operation metadata and the extension values. The generated YAML contract is then fed
through SCC's `YamlToContracts` to produce the final `Contract` objects.

## API

```java
// Automatic via SCC ContractConverter SPI -- no direct API call needed.
// SCC plugin discovers OpenApiContractConverter through spring.factories.

// Manual usage:
OpenApiContractConverter converter = new OpenApiContractConverter();
boolean accepted = converter.isAccepted(new File("openapi.yaml"));
Collection<Contract> contracts = converter.convertFrom(new File("openapi.yaml"));
```

### x-contracts Extension Structure

```yaml
paths:
  /users/{id}:
    get:
      operationId: getUser
      x-contracts:
        - contractId: 1
          name: "get user by id"
          description: "Returns a user"
          priority: 1
          label: "get_user"
          ignored: false
          serviceName: "user-service"
          contractPath: "/users/123"
          headers:
            Accept: "application/json"
          request:
            queryParameters:
              - key: "verbose"
                value: "true"
      parameters:
        - name: id
          in: path
          x-contracts:
            - contractId: 1
              value: "123"
      responses:
        '200':
          x-contracts:
            - contractId: 1
              body: '{"id": 123, "name": "John"}'
              headers:
                X-Custom: "value"
```

### Contract Properties from x-contracts

| Property | Source | Description |
|----------|--------|-------------|
| `name` | `x-contracts[].name` | Contract name |
| `description` | `x-contracts[].description` | Contract description |
| `priority` | `x-contracts[].priority` | Contract priority |
| `label` | `x-contracts[].label` | Contract label |
| `ignored` | `x-contracts[].ignored` | Whether contract is ignored |
| `contractPath` | `x-contracts[].contractPath` | Override URL path |
| `serviceName` | `x-contracts[].serviceName` | Filter by service name |

## Business Rules

1. Only operations with `x-contracts` extensions are converted; operations without them are ignored
2. Each `x-contracts` entry produces one SCC contract, keyed by `contractId`
3. The HTTP method is derived from the operation's position in the path item (GET, POST, PUT, DELETE, PATCH)
4. The request URL path defaults to the OpenAPI path with path parameter placeholders replaced by `x-contracts` values from the parameter definition
5. `contractPath` in the extension overrides the calculated path
6. Request headers are merged from: operation-level `x-contracts` headers, parameter-level header `x-contracts`, request body `x-contracts` headers, and the OpenAPI request body content type
7. Query parameters come from operation-level parameter `x-contracts` and request body `x-contracts`
8. Cookies come from cookie-type parameter `x-contracts` and request body `x-contracts`
9. Request body, `bodyFromFile`, and `bodyFromFileAsBytes` come from request body `x-contracts`
10. Multipart request support includes `params` and `named` file parts with all SCC multipart fields
11. Response status is parsed from the OpenAPI response key (e.g., `200`, `201`)
12. Response body and headers come from response-level `x-contracts`
13. Response Content-Type is derived from the OpenAPI response content media type
14. Matchers (body, headers, cookies, query parameters, URL, multipart) support `regex`, `predefined`, `command`, `regexType`, `type`, `minOccurrence`, `maxOccurrence`
15. The `serviceName` property enables filtering contracts by service via the `scc.enabled.service-names` system property (comma-separated list); if the property is unset, all contracts are included
16. File acceptance checks the first non-comment line for `openapi`, `swagger`, `"openapi"`, `"swagger"`, or `{` to quickly reject non-OpenAPI files
17. `convertTo` (Contract to OpenAPI) is not supported and throws `UnsupportedOperationException`

## Acceptance Criteria

### Basic GET Conversion

**Given** an OpenAPI spec with `GET /users/{id}` and `x-contracts` with `contractId: 1`
**And** a path parameter `x-contracts` with `contractId: 1, value: "123"`
**And** a response `200` with `x-contracts` containing a body
**When** SCC loads the file via `OpenApiContractConverter`
**Then** one Contract is produced with method GET, URL `/users/123`, and status 200

### POST with Request Body

**Given** an OpenAPI spec with `POST /users` and a request body with `x-contracts`
**And** the request body extension contains `body: '{"name": "John"}'`
**And** the request content type is `application/json`
**When** the converter processes the file
**Then** the contract has Content-Type header `application/json` and the specified body

### Multiple Contracts Per Operation

**Given** an OpenAPI operation with two `x-contracts` entries (contractId 1 and 2)
**And** corresponding parameter and response `x-contracts` for both IDs
**When** the converter processes the file
**Then** two separate Contract objects are produced

### Service Name Filtering

**Given** an OpenAPI spec with contracts for `serviceName: "order-service"` and `serviceName: "user-service"`
**And** the system property `scc.enabled.service-names` is set to `"order-service"`
**When** the converter processes the file
**Then** only contracts for `order-service` are produced

### Non-OpenAPI File Rejected

**Given** a YAML file that starts with `spring:` (not an OpenAPI spec)
**When** `isAccepted` is called
**Then** it returns `false`

### Matchers Are Preserved

**Given** an OpenAPI spec with `x-contracts` containing body matchers with `regex` and `type`
**When** the converter processes the file
**Then** the resulting contract includes the matchers with correct regex and type values

### Response Headers from Content Type

**Given** an OpenAPI response with `content: application/json`
**And** additional custom headers in `x-contracts`
**When** the converter processes the file
**Then** the response has both `Content-Type: application/json` and the custom headers

## Error Cases

| Scenario | Behavior |
|----------|----------|
| File is not YAML/JSON | `isAccepted` returns `false` |
| File does not start with OpenAPI indicators | `isAccepted` returns `false` |
| OpenAPI spec has no paths | `isAccepted` returns `false` |
| No operations have `x-contracts` | `isAccepted` returns `false`, `convertFrom` returns empty list |
| `convertTo` called | Throws `UnsupportedOperationException` |
| Malformed `x-contracts` extension | `convertFrom` returns empty list, error logged |
