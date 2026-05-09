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
package sh.stubborn.openapi.converter;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.contract.spec.Contract;
import sh.stubborn.openapi.validation.OpenApiContractDriftException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConverterDriftCheckTest {

	private static final String DRIFT_PROPERTY = "scc.oa3.converter.drift";

	private static final String OPENAPI_FOO_GET_200 = """
			openapi: 3.0.1
			info:
			  title: Drift Test API
			  version: 1.0.0
			paths:
			  /foo:
			    get:
			      responses:
			        '200':
			          description: OK
			""";

	@BeforeEach
	@AfterEach
	void clearProperty() {
		System.clearProperty(DRIFT_PROPERTY);
	}

	@Test
	void should_throw_in_default_mode_when_drift_detected() {
		List<Contract> drifted = List.of(contract("POST", "/missing", 404));

		assertThatThrownBy(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted))
			.isInstanceOf(OpenApiContractDriftException.class)
			.satisfies(ex -> assertThat(((OpenApiContractDriftException) ex).report().hasViolations()).isTrue());
	}

	@Test
	void should_throw_in_explicit_fail_mode_when_drift_detected() {
		System.setProperty(DRIFT_PROPERTY, "fail");
		List<Contract> drifted = List.of(contract("POST", "/missing", 404));

		assertThatThrownBy(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted))
			.isInstanceOf(OpenApiContractDriftException.class);
	}

	@Test
	void should_not_throw_in_warn_mode_when_drift_detected() {
		System.setProperty(DRIFT_PROPERTY, "warn");
		List<Contract> drifted = List.of(contract("POST", "/missing", 404));

		assertThatCode(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted)).doesNotThrowAnyException();
	}

	@Test
	void should_not_throw_in_off_mode_even_when_drift_present() {
		System.setProperty(DRIFT_PROPERTY, "off");
		List<Contract> drifted = List.of(contract("POST", "/missing", 404));

		assertThatCode(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted)).doesNotThrowAnyException();
	}

	@Test
	void should_treat_unknown_mode_as_fail() {
		System.setProperty(DRIFT_PROPERTY, "banana");
		List<Contract> drifted = List.of(contract("POST", "/missing", 404));

		assertThatThrownBy(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted))
			.isInstanceOf(OpenApiContractDriftException.class);
	}

	@Test
	void should_not_throw_when_contracts_match_spec() {
		List<Contract> matching = List.of(contract("GET", "/foo", 200));

		assertThatCode(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, matching)).doesNotThrowAnyException();
	}

	@Test
	void should_be_case_insensitive_for_mode() {
		System.setProperty(DRIFT_PROPERTY, "  WARN ");
		List<Contract> drifted = List.of(contract("POST", "/missing", 404));

		assertThatCode(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted)).doesNotThrowAnyException();
	}

	private static Contract contract(String method, String url, int status) {
		return Contract.make(c -> {
			c.request(r -> {
				r.method(method);
				r.urlPath(url);
			});
			c.response(r -> r.status(status));
		});
	}

}
