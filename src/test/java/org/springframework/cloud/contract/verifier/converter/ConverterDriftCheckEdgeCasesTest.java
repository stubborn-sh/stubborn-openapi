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
package org.springframework.cloud.contract.verifier.converter;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.verifier.openapivalidation.OpenApiContractDriftException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edge-case adversarial tests for {@link ConverterDriftCheck}.
 *
 * Spec reference: docs/specs/006-converter-drift-detection.md Business Rule 1: drift
 * verification runs only when at least one contract was produced. Business Rule 7: system
 * property re-read on every call (no caching). Business Rule 8: unknown / empty values
 * fall back to fail.
 */
class ConverterDriftCheckEdgeCasesTest {

	private static final String DRIFT_PROPERTY = "scc.oa3.converter.drift";

	private static final String OPENAPI_FOO_GET_200 = """
			openapi: 3.0.1
			info:
			  title: Test API
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

	/**
	 * SPEC Business Rule 1: drift verification runs only when at least one contract was
	 * produced. Empty collection must be silently skipped even in fail mode.
	 *
	 * ConverterDriftCheck.java:49 has the guard but no test covers the
	 * empty-contracts-in-fail-mode branch explicitly.
	 */
	@Test
	void should_not_throw_for_empty_contracts_in_fail_mode() {
		assertThatCode(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, Collections.emptyList()))
			.as("Empty contract list must be silently skipped even in fail mode per Business Rule 1")
			.doesNotThrowAnyException();
	}

	/**
	 * SPEC Business Rule 7: system property is re-read on every convertFrom call (no
	 * caching). If mode were cached, a change from fail to warn between calls would not
	 * take effect.
	 */
	@Test
	void should_re_read_system_property_between_calls() {
		List<Contract> drifted = List.of(contract("POST", "/missing", 404));

		System.setProperty(DRIFT_PROPERTY, "fail");
		try {
			ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted);
		}
		catch (Exception ignored) {
			// expected to throw
		}

		System.setProperty(DRIFT_PROPERTY, "warn");
		assertThatCode(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted))
			.as("Mode must be re-read from system property on every call — no caching allowed per Business Rule 7")
			.doesNotThrowAnyException();
	}

	/**
	 * SPEC Business Rule 8: unknown mode falls back to fail. When there is NO drift,
	 * fall- back to fail must not produce a spurious exception.
	 */
	@Test
	void should_not_throw_for_unknown_mode_when_no_drift() {
		System.setProperty(DRIFT_PROPERTY, "banana");
		List<Contract> matching = List.of(contract("GET", "/foo", 200));

		assertThatCode(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, matching))
			.as("Unknown mode falls back to fail; with no drift no exception must be raised")
			.doesNotThrowAnyException();
	}

	/**
	 * SPEC Business Rule 8: empty string value is trimmed then mapped to "fail". Verify
	 * that an empty system property value triggers fail semantics on drift.
	 *
	 * The switch in resolveMode() has case "fail", "" -> Mode.FAIL so this maps to FAIL.
	 * No existing test covers the empty-string path.
	 */
	@Test
	void should_treat_empty_string_property_as_fail_mode() {
		System.setProperty(DRIFT_PROPERTY, "");
		List<Contract> drifted = List.of(contract("POST", "/missing", 404));

		assertThatThrownBy(() -> ConverterDriftCheck.apply(OPENAPI_FOO_GET_200, drifted))
			.as("Empty string property value must behave as fail mode per Business Rule 8")
			.isInstanceOf(OpenApiContractDriftException.class);
	}

	/**
	 * SPEC Error Cases table: "Verifier itself throws -> rethrown as
	 * OpenApiContractDriftException". ConverterDriftCheck.java line 57-59 rethrows the
	 * raw RuntimeException without wrapping it in OpenApiContractDriftException. This is
	 * a spec/impl mismatch.
	 *
	 * We cannot inject a broken verifier through the package-private static field, so
	 * this test verifies the observable contract for null content: must not leak NPE.
	 */
	@Test
	void should_not_leak_npe_for_null_openapi_content() {
		List<Contract> contracts = List.of(contract("GET", "/foo", 200));

		try {
			ConverterDriftCheck.apply((String) null, contracts);
			// Acceptable path: null content treated as "parse failure", returns empty
			// report, no violations, no throw. Correct behavior.
		}
		catch (OpenApiContractDriftException ex) {
			// Also acceptable: null content caused parse error which was turned into
			// violations, and mode=fail caused a drift exception.
			assertThat(ex.report()).isNotNull();
		}
		catch (NullPointerException npe) {
			org.assertj.core.api.Assertions.fail("NullPointerException must not escape ConverterDriftCheck.apply — "
					+ "null openApiContent must be handled gracefully", npe);
		}
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
