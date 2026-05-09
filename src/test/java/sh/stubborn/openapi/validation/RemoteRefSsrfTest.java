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
package sh.stubborn.openapi.validation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a malicious OpenAPI spec containing remote {@code $ref} URLs cannot make
 * the build JVM open outbound HTTP connections during parsing — the SSRF mitigation
 * required by spec 006.
 */
class RemoteRefSsrfTest {

	private HttpServer server;

	private AtomicInteger callCount;

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

	@Test
	void should_not_fetch_remote_ref_when_parsing_openapi_in_memory() {
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

		new OpenApiContractsVerifier().verifyInMemory(openApi, "");

		assertThat(this.callCount.get()).as("Parser must not fetch remote $ref URLs during in-memory parsing").isZero();
	}

}
