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
 * Schema-level drift acceptance tests for {@link OpenApiContractsVerifier} — covers
 * request/response body schemas, headers, query/path params and content types as
 * specified in {@code docs/specs/006-converter-drift-detection.md}.
 */
class SchemaDriftTest {

	private final OpenApiContractsVerifier verifier = new OpenApiContractsVerifier();

	@Test
	void should_report_request_body_schema_drift() {
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
				              required: [customerId]
				              properties:
				                customerId: { type: string }
				      responses:
				        '201': { description: Created }
				""";
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/orders");
				r.headers(h -> h.contentType("application/json"));
				r.body(Map.of("customer_id", 42));
			});
			c.response(rsp -> rsp.status(201));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations()).isTrue();
		assertThat(report.render()).containsIgnoringCase("customerId");
	}

	@Test
	void should_accept_request_body_matching_schema() {
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
				              required: [customerId]
				              properties:
				                customerId: { type: string }
				      responses:
				        '201': { description: Created }
				""";
		Contract good = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/orders");
				r.headers(h -> h.contentType("application/json"));
				r.body(Map.of("customerId", "abc-123"));
			});
			c.response(rsp -> rsp.status(201));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(good));

		assertThat(report.hasViolations()).isFalse();
	}

	@Test
	void should_report_response_body_schema_drift() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /orders/{id}:
				    get:
				      parameters:
				        - { name: id, in: path, required: true, schema: { type: string } }
				      responses:
				        '200':
				          description: OK
				          content:
				            application/json:
				              schema:
				                type: object
				                required: [id, total]
				                properties:
				                  id: { type: string }
				                  total: { type: number }
				""";
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/orders/abc");
			});
			c.response(rsp -> {
				rsp.status(200);
				rsp.headers(h -> h.contentType("application/json"));
				rsp.body(Map.of("id", "abc"));
			});
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations()).isTrue();
		assertThat(report.render()).containsIgnoringCase("total");
	}

	@Test
	void should_report_missing_required_query_parameter() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /events:
				    get:
				      parameters:
				        - { name: since, in: query, required: true, schema: { type: string } }
				      responses:
				        '200': { description: OK }
				""";
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/events");
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations()).isTrue();
		assertThat(report.render()).containsIgnoringCase("since");
	}

	@Test
	void should_report_query_parameter_type_mismatch() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /events:
				    get:
				      parameters:
				        - { name: limit, in: query, required: true, schema: { type: integer } }
				      responses:
				        '200': { description: OK }
				""";
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/events", url -> url.queryParameters(qp -> qp.parameter("limit", "banana")));
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations()).isTrue();
		assertThat(report.render()).containsIgnoringCase("integer");
	}

	@Test
	void should_report_missing_required_request_header() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /orders:
				    get:
				      parameters:
				        - { name: X-Tenant-Id, in: header, required: true, schema: { type: string } }
				      responses:
				        '200': { description: OK }
				""";
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("GET");
				r.urlPath("/orders");
			});
			c.response(rsp -> rsp.status(200));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations()).isTrue();
		assertThat(report.render()).containsIgnoringCase("X-Tenant-Id");
	}

	@Test
	void should_report_unsupported_request_content_type() {
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
				        '201': { description: Created }
				""";
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/orders");
				r.headers(h -> h.contentType("application/xml"));
				r.body("<order/>");
			});
			c.response(rsp -> rsp.status(201));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations()).isTrue();
		assertThat(report.render()).containsIgnoringCase("application/xml");
	}

	@Test
	void should_skip_schema_check_when_path_method_status_already_drifted() {
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /foo:
				    get:
				      responses:
				        '200': { description: OK }
				""";
		Contract drifted = Contract.make(c -> {
			c.request(r -> {
				r.method("POST");
				r.urlPath("/missing");
				r.body(Map.of("x", 1));
			});
			c.response(rsp -> rsp.status(404));
		});

		OpenApiVerificationReport report = verifier.verifyInMemory(openApi, List.of(drifted));

		assertThat(report.hasViolations()).isTrue();
		// Exactly one violation — the path/method drift; schema-level checks are skipped.
		assertThat(report.violations()).hasSize(1);
		assertThat(report.render()).containsIgnoringCase("/missing");
	}

}
