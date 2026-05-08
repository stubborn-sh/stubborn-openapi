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

import org.junit.jupiter.api.Test;

import org.springframework.cloud.contract.spec.Contract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial tests for SchemaDriftValidator.isPathParamTypeMismatchOnRegex suppression
 * over-reach.
 *
 * Spec reference: docs/specs/006-converter-drift-detection.md
 *
 * Business Rule 11 says path parameter VALUES that are regex matchers are not
 * type-checked. The heuristic in SchemaDriftValidator uses REGEX_PATH_HINT to detect
 * regex-shaped paths. The heuristic matches on ANY path containing '[', '\', or '(' --
 * and then suppresses ANY violation whose key contains "type" or whose message contains
 * "does not match". This is far broader than rule 11 which only covers path-parameter
 * type-checks.
 *
 * Consequence: when a path contains a literal parenthesis (e.g., "/v1/orders/(pending)")
 * ALL type-related violations are suppressed -- including required query parameter type
 * mismatches and required header violations that coincidentally have "type" in their
 * validation key. This makes drift go undetected.
 */
class SchemaDriftSuppressionOverreachTest {

	private final OpenApiContractsVerifier verifier = new OpenApiContractsVerifier();

	/**
	 * IMPL_BUG (P0): A path containing a literal parenthesis -- a valid, non-regex URL --
	 * triggers REGEX_PATH_HINT (".*[\[\\(].*"). Any violation whose key contains "type"
	 * (e.g., a query parameter type mismatch) is then suppressed. But Business Rule 11
	 * only exempts PATH PARAMETER type checks for regex-valued params. A query-param type
	 * mismatch on a path that happens to have a '(' must still be reported.
	 *
	 * Spec acceptance criterion "Query Parameter Type Mismatch": Given spec declaring
	 * 'limit' as integer on GET /events, when contract calls with limit=banana, the
	 * report must contain a violation. This must hold even when the path itself contains
	 * a parenthesis.
	 */
	@Test
	void should_report_query_param_type_mismatch_even_when_path_contains_literal_parenthesis() {
		// Path deliberately contains '(' -- a literal parenthesis, not a regex marker.
		// REGEX_PATH_HINT matches it, which triggers over-broad suppression.
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /v1/orders/(pending):
				    get:
				      parameters:
				        - name: limit
				          in: query
				          required: false
				          schema: { type: integer }
				      responses:
				        '200': { description: OK }
				""";

		// Contract calls with limit=banana -- clear type mismatch (string vs integer).
		// urlPath lambda form is the correct SCC DSL for attaching query params.
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/v1/orders/(pending)", url -> url.queryParameters(qp -> qp.parameter("limit", "banana")));
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations())
			.as("Query param 'limit' type mismatch (banana vs integer) must be reported even though "
					+ "path contains literal '(' which matches REGEX_PATH_HINT. "
					+ "isPathParamTypeMismatchOnRegex must not suppress query-param type violations. "
					+ "Spec AC: 'Query Parameter Type Mismatch'")
			.isTrue();
	}

	/**
	 * IMPL_BUG (P0): The looksLikeTypeMismatch check in isPathParamTypeMismatchOnRegex
	 * fires when the Atlassian validation key contains "type". The key
	 * "validation.request.contentType.notAllowed" contains "type". So a content-type
	 * violation on a regex-path is suppressed even though Business Rule 11 only exempts
	 * PATH PARAMETER type checks.
	 *
	 * Spec acceptance criterion "Content Type Drift": content-type violations must always
	 * be reported.
	 */
	@Test
	void should_report_content_type_violation_even_when_path_contains_bracket() {
		// Path contains '[' which matches REGEX_PATH_HINT
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /orders/[status]:
				    post:
				      requestBody:
				        required: true
				        content:
				          application/json:
				            schema: { type: object }
				      responses:
				        '200': { description: OK }
				""";

		// Contract sends application/xml -- not in the spec's declared content types
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/orders/[status]");
				r.headers(h -> h.contentType("application/xml"));
				r.body("<order/>");
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations())
			.as("Content-type 'application/xml' is not declared in spec -- must be a violation. "
					+ "The path contains '[' which triggers REGEX_PATH_HINT. "
					+ "Content-type violations must not be suppressed by rule-11 path-param heuristic. "
					+ "Spec AC: 'Content Type Drift'")
			.isTrue();
	}

	/**
	 * IMPL_BUG (P0): Distinguishes a LITERAL non-integer path segment from a regex one.
	 * The spec AC "Path Parameter Type Mismatch" says a literal "abc" in an integer path
	 * param MUST be reported. The suppression heuristic fires when the path CONTAINS '[',
	 * '\', or '('. The path "/orders/abc" does NOT contain those chars, so the
	 * suppression should NOT fire and the violation should be reported.
	 *
	 * If this test passes but the content-type test above fails, it confirms the
	 * over-reach is specifically triggered by the path-regex heuristic on non-regex
	 * paths.
	 */
	@Test
	void should_report_path_param_type_mismatch_for_literal_value_without_regex_chars() {
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

		// "abc" has no regex chars -- path hint must NOT fire -- violation must be
		// reported
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/orders/abc");
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations())
			.as("Literal 'abc' for integer path param must produce a type mismatch violation. "
					+ "Path '/orders/abc' has no regex chars so REGEX_PATH_HINT must not fire. "
					+ "Spec AC: 'Path Parameter Type Mismatch'")
			.isTrue();
	}

}
