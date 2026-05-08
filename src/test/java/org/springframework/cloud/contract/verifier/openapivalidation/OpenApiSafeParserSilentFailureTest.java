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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial tests for OpenApiSafeParser and the file-based parse path.
 *
 * Spec reference: docs/specs/006-converter-drift-detection.md -- "Remote $ref Refused At
 * Parse Time" acceptance criterion, SSRF mitigation section.
 *
 * The spec states: "a malicious spec containing $ref: 'https://attacker.invalid/x' cannot
 * make the build JVM open outbound connections". The RemoteRefSsrfTest only covers the
 * in-memory (string) parse path via verifyInMemory(String, String). The file-based parse
 * path -- OpenApiSafeParser.parsePath -> OpenApiContractsVerifier.verify(Path, Path) --
 * is NOT tested for SSRF. Both paths use the same safeOptions() so the mitigation should
 * hold, but the acceptance criterion is not verified for that path.
 */
class OpenApiSafeParserSilentFailureTest {

	private HttpServer server;

	private AtomicInteger callCount;

	@TempDir
	Path tempDir;

	@BeforeEach
	void startCanaryServer() throws IOException {
		this.callCount = new AtomicInteger();
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/schema", exchange -> {
			this.callCount.incrementAndGet();
			byte[] response = "{\"type\":\"string\"}".getBytes();
			exchange.sendResponseHeaders(200, response.length);
			try (var os = exchange.getResponseBody()) {
				os.write(response);
			}
		});
		this.server.start();
	}

	@AfterEach
	void stopCanaryServer() {
		if (this.server != null) {
			this.server.stop(0);
		}
	}

	/**
	 * SPEC_GAP (P1): RemoteRefSsrfTest verifies SSRF mitigation only for the in-memory
	 * string path. The file-based path (OpenApiSafeParser.parsePath used by
	 * OpenApiContractsVerifier.verify(Path, Path)) must also not fetch remote $ref URLs.
	 *
	 * Spec AC: "Remote $ref Refused At Parse Time" -- "When convertFrom (or
	 * verifyInMemory(String, ...)) parses it, the parser does not open an outbound HTTP
	 * connection". The file-based verify(Path, Path) path is an equivalent entry point
	 * and must also be SSRF-safe.
	 */
	@Test
	void should_not_fetch_remote_ref_when_parsing_openapi_from_file() throws IOException {
		int port = this.server.getAddress().getPort();
		String openApi = """
				openapi: 3.0.1
				info: { title: t, version: '1' }
				paths:
				  /probe:
				    get:
				      responses:
				        '200':
				          description: OK
				          content:
				            application/json:
				              schema:
				                $ref: 'http://127.0.0.1:%d/schema'
				""".formatted(port);

		Path specFile = tempDir.resolve("ssrf-test.yaml");
		Files.writeString(specFile, openApi);
		Path contractsDir = tempDir.resolve("contracts");
		Files.createDirectories(contractsDir);
		// Empty contracts dir -- we only care that parsing doesn't fetch remote refs.

		new OpenApiContractsVerifier().verify(specFile, contractsDir);

		assertThat(this.callCount.get())
			.as("Parser must not fetch remote $ref URLs when reading OpenAPI from a file path. "
					+ "SSRF mitigation must apply to file-based parse path, not just in-memory parsing. "
					+ "Spec AC: 'Remote $ref Refused At Parse Time'")
			.isZero();
	}

	/**
	 * Spec error case: parse-error messages from
	 * {@link io.swagger.v3.parser.core.models.SwaggerParseResult#getMessages()} must be
	 * surfaced as violations. With {@code resolve=false} (SSRF mitigation) unresolved
	 * {@code $ref} does not produce a message — but malformed YAML does, and that path
	 * must not be silently dropped.
	 */
	@Test
	void should_expose_safeOptions_publicly_for_cross_package_reuse() {
		// Both Oa3Parser (converter package) and OpenApiSafeParser (openapivalidation
		// package) need the same parse options; safeOptions() must be public so the
		// converter can reuse it without duplicating the SSRF-mitigation flags.
		var options = OpenApiSafeParser.safeOptions();
		assertThat(options.isResolve()).isFalse();
		assertThat(options.isResolveFully()).isFalse();
		assertThat(options.isResolveCombinators()).isFalse();
		assertThat(options.isResolveRequestBody()).isFalse();
		assertThat(options.isResolveResponses()).isFalse();
	}

	@Test
	void should_surface_parse_messages_when_spec_is_malformed_yaml() {
		String malformed = "openapi: 3.0.1\ninfo: { title: t, version: '1'\npaths: {{:";

		OpenApiVerificationReport report = new OpenApiContractsVerifier().verifyInMemory(malformed, "");

		assertThat(report.hasViolations())
			.as("Malformed YAML must surface parse-error messages from the parser, "
					+ "not silently produce a 'no paths' message")
			.isTrue();
		assertThat(report.render()).containsIgnoringCase("parse");
	}

}
