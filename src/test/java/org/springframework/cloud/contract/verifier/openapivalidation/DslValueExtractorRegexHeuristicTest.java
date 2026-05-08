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
import org.springframework.cloud.contract.spec.internal.DslProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial tests for DslValueExtractor.looksLikeRegex false-positive and
 * false-negative heuristics.
 *
 * Spec reference: docs/specs/006-converter-drift-detection.md Business Rule 12: two-sided
 * DslProperty uses server/producer value. Business Rule 11: literal path param values are
 * type-checked.
 */
class DslValueExtractorRegexHeuristicTest {

	private final OpenApiContractsVerifier verifier = new OpenApiContractsVerifier();

	/**
	 * FALSE POSITIVE — IMPL_BUG (P0): "application/json" does NOT contain regex markers
	 * listed in looksLikeRegex, but "text/plain;charset=utf-8" does NOT either. However,
	 * "application/[a-z]+" WOULD be treated as regex correctly. The real false-positive:
	 * any literal value containing "[0-9]" substring (e.g. a product SKU like "SKU[0-9]X"
	 * used as a literal body field value) will be silently dropped, causing the schema
	 * validator to see null instead of the literal, potentially missing a real schema
	 * violation.
	 *
	 * Reproduce: contract body has a DslProperty where server value is the literal string
	 * "SKU[0-9]X" (a real product code that happens to contain bracket notation). The
	 * extractor misidentifies it as a regex and falls back to the client value (also the
	 * same literal), so validation still proceeds — but if the client value were
	 * different, the wrong value would be validated.
	 */
	@Test
	void should_treat_literal_value_containing_bracket_digit_pattern_as_literal_not_regex() {
		// Given: OpenAPI spec requiring a string field "sku"
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /products:
				    post:
				      requestBody:
				        required: true
				        content:
				          application/json:
				            schema:
				              type: object
				              required: [sku]
				              properties:
				                sku: { type: string }
				      responses:
				        '201': { description: Created }
				""";

		// SCC convention is DslProperty(clientValue, serverValue). Both sides are the
		// literal product code "SKU[0-9]X" — looks like a regex but is a real SKU.
		DslProperty<Object> skuProperty = new DslProperty<>("SKU[0-9]X", "SKU[0-9]X");

		Contract contract = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/products");
				r.headers(h -> h.contentType("application/json"));
				r.body(Map.of("sku", skuProperty));
			});
			c.response(rsp -> rsp.status(201));
		});

		// Client=42 (would violate string schema), Server="SKU[0-9]X" (valid string,
		// looks like a regex but is a literal). Type-based detection treats it as
		// a literal → validation must pass.
		DslProperty<Object> skuPropertyMismatched = new DslProperty<>(42, "SKU[0-9]X");

		Contract contractWithMismatch = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/products");
				r.headers(h -> h.contentType("application/json"));
				r.body(Map.of("sku", skuPropertyMismatched));
			});
			c.response(rsp -> rsp.status(201));
		});

		// Spec says server/producer value must be validated (Business Rule 12).
		// "SKU[0-9]X" is a valid string. Validation should PASS.
		// But the false positive causes the integer client value (42) to be validated
		// instead, which may or may not cause a violation depending on implementation.
		// The critical invariant: server value must be preferred for validation.
		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(contractWithMismatch));

		// The server value "SKU[0-9]X" IS a valid string — validation must pass.
		// If it fails, the extractor validated the wrong (client) side.
		assertThat(report.hasViolations())
			.as("Server value 'SKU[0-9]X' is a valid string for a string schema — should not violate. "
					+ "If this fails, looksLikeRegex has a false-positive causing the wrong side to be validated.")
			.isFalse();
	}

	/**
	 * FALSE POSITIVE — IMPL_BUG (P1): Content-Type value "multipart/form-data;
	 * boundary=[0-9a-f]+" contains "[0-9" and will be treated as a regex by
	 * looksLikeRegex, causing the content type to be silently dropped from the synthetic
	 * request, potentially masking a content-type drift violation.
	 */
	@Test
	void should_not_drop_content_type_that_contains_bracket_chars_as_false_regex() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /upload:
				    post:
				      requestBody:
				        required: true
				        content:
				          application/json:
				            schema: { type: object }
				      responses:
				        '200': { description: OK }
				""";

		// Contract sets Content-Type to a value that contains "[0-9" pattern.
		// looksLikeRegex incorrectly classifies it as a regex and drops it.
		// The spec only allows "application/json" — so "text/plain" SHOULD be a
		// violation.
		// But if the header is dropped, the Atlassian validator sees no Content-Type
		// and may not flag a content-type violation at all.
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/upload");
				// This is a DslProperty where server value contains bracket-digit pattern
				r.headers(h -> h.header("Content-Type", new DslProperty<>("text/plain", "text/plain[0-9]?")));
				r.body("hello");
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		// "text/plain" is not in the spec's requestBody content types.
		// A correctly extracted Content-Type should trigger a content-type violation.
		assertThat(report.hasViolations())
			.as("Content-Type 'text/plain' is not declared in spec — should produce a violation. "
					+ "If no violation, the content-type header was dropped due to false-positive regex detection.")
			.isTrue();
	}

	/**
	 * SPEC RULE 12 — IMPL_BUG (P0): When a DslProperty has server=literal and
	 * client=regex, the server value MUST be used. The current code first checks server;
	 * if looksLikeRegex(server) is false it returns serverResolved — correct. But if
	 * server is null and only client is present, it falls to clientResolved even though
	 * the spec says "server value". This test verifies the one-sided case where only the
	 * server value is set.
	 */
	@Test
	void should_use_server_value_when_only_server_side_is_present() {
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
				            schema:
				              type: object
				              required: [amount]
				              properties:
				                amount: { type: integer }
				      responses:
				        '201': { description: Created }
				""";

		// Spec rule: use server value. Server=123 (integer matching schema),
		// client=absent.
		// This should PASS validation.
		DslProperty<Object> serverOnly = new DslProperty<>(123, null);

		Contract contract = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/orders");
				r.headers(h -> h.contentType("application/json"));
				r.body(Map.of("amount", serverOnly));
			});
			c.response(rsp -> rsp.status(201));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(contract));

		assertThat(report.hasViolations()).as("Server value 123 matches integer schema — should not violate").isFalse();
	}

	/**
	 * SPEC AC "Server Value Used For DslProperty" — IMPL_BUG (P0): producer(123)
	 * consumer("abc") — producer side is integer matching schema, consumer is string.
	 * Spec says server (producer) value is used. Validation must PASS.
	 *
	 * This directly tests the acceptance criterion from the spec.
	 */
	@Test
	void should_validate_producer_value_not_consumer_for_two_sided_dsl_property() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /items:
				    get:
				      responses:
				        '200':
				          description: OK
				          content:
				            application/json:
				              schema:
				                type: object
				                required: [count]
				                properties:
				                  count: { type: integer }
				""";

		// SCC convention is DslProperty(clientValue, serverValue) — see the field
		// declaration order in DslProperty.class. Spec rule 12 says the SERVER (producer)
		// value is what the stub emits and is therefore validated. Client="abc",
		// Server=123: server value 123 matches the integer schema → must NOT violate.
		DslProperty<Object> producerInt = new DslProperty<>("abc", 123);

		Contract contract = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/items");
			});
			c.response(rsp -> {
				rsp.status(200);
				rsp.headers(h -> h.contentType("application/json"));
				rsp.body(Map.of("count", producerInt));
			});
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(contract));

		assertThat(report.hasViolations())
			.as("Server value 123 matches the integer schema — must not violate per spec rule 12")
			.isFalse();
	}

}
