/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.contract.verifier.openapivalidation;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.contract.spec.Contract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for acceptance criteria in docs/specs/006-converter-drift-detection.md that are
 * NOT covered by SchemaDriftTest:
 *
 * - AC "Required Response Header Missing" - AC "Path Parameter Type Mismatch" - Business
 * Rule 10: absent body does not violate when spec marks requestBody optional - Business
 * Rule 11: regex path param values are not type-checked
 */
class SchemaDriftMissingCriteriaTest {

	private final OpenApiContractsVerifier verifier = new OpenApiContractsVerifier();

	/**
	 * IMPL_BUG: Acceptance criterion "Required Response Header Missing" has zero test
	 * coverage. The Atlassian validator checks required response headers; this test
	 * verifies that the SchemaDriftValidator actually surfaces them.
	 *
	 * Spec AC: Given spec declares Location as required response header on POST /orders
	 * 201, when contract omits Location, report must name the missing header.
	 */
	@Test
	void should_report_missing_required_response_header() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /orders:
				    post:
				      requestBody:
				        required: true
				        content:
				          application/json:
				            schema: { type: object }
				      responses:
				        '201':
				          description: Created
				          headers:
				            Location:
				              required: true
				              schema: { type: string }
				""";

		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/orders");
				r.headers(h -> h.contentType("application/json"));
				r.body(Map.of("name", "test"));
			});
			c.response(rsp -> rsp.status(201));
			// Location header intentionally omitted from response
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations())
			.as("Missing required response header 'Location' must be a violation "
					+ "per spec AC 'Required Response Header Missing'")
			.isTrue();
		assertThat(report.render()).containsIgnoringCase("Location");
	}

	/**
	 * IMPL_BUG: Acceptance criterion "Path Parameter Type Mismatch" has zero test
	 * coverage in SchemaDriftTest. The spec declares id as integer; the contract uses
	 * literal "abc".
	 */
	@Test
	void should_report_path_parameter_type_mismatch() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /orders/{id}:
				    get:
				      parameters:
				        - { name: id, in: path, required: true, schema: { type: integer } }
				      responses:
				        '200': { description: OK }
				""";

		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/orders/abc"); // "abc" is not an integer — literal non-integer
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations())
			.as("Path param 'id' declared integer but contract uses literal 'abc' — "
					+ "must violate per spec AC 'Path Parameter Type Mismatch'")
			.isTrue();
	}

	/**
	 * IMPL_BUG: Business Rule 10: contracts with no request body must NOT trigger
	 * body-schema violations when the spec marks requestBody as optional (required:
	 * false). No test currently exercises this rule.
	 */
	@Test
	void should_not_report_body_violation_when_body_absent_and_spec_marks_it_optional() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /orders:
				    post:
				      requestBody:
				        required: false
				        content:
				          application/json:
				            schema:
				              type: object
				              properties:
				                note: { type: string }
				      responses:
				        '201': { description: Created }
				""";

		Contract contract = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/orders");
				// No body — requestBody is optional in the spec
			});
			c.response(rsp -> rsp.status(201));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(contract));

		assertThat(report.hasViolations())
			.as("Absent request body must not violate when spec marks requestBody optional per Business Rule 10")
			.isFalse();
	}

	/**
	 * IMPL_BUG: Business Rule 11: path parameter values that are regex matchers are NOT
	 * type-checked against the spec path parameter schema. No test currently covers this
	 * rule. If the implementation type-checks the raw regex string against the integer
	 * schema it will produce a spurious violation.
	 */
	@Test
	void should_not_type_check_path_parameter_when_value_is_regex_matcher() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /orders/{id}:
				    get:
				      parameters:
				        - { name: id, in: path, required: true, schema: { type: integer } }
				      responses:
				        '200': { description: OK }
				""";

		// Path contains an SCC regex matcher pattern — not a literal value.
		// Business Rule 11: must NOT be type-checked against the integer schema.
		Contract contract = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/orders/[0-9]+"); // regex matcher string
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(contract));

		assertThat(report.hasViolations())
			.as("Regex path parameter '[0-9]+' must not be type-checked against integer schema "
					+ "per Business Rule 11")
			.isFalse();
	}

}
