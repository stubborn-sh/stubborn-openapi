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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import org.springframework.cloud.contract.spec.Contract;
import sh.stubborn.openapi.validation.OpenApiContractDriftException;
import sh.stubborn.openapi.validation.OpenApiContractViolation;
import sh.stubborn.openapi.validation.OpenApiContractsVerifier;
import sh.stubborn.openapi.validation.OpenApiVerificationReport;

/**
 * Verifies contracts produced by {@link OpenApiContractConverter} against the OpenAPI
 * specification they were generated from. Behaviour is controlled by the
 * {@code scc.oa3.converter.drift} system property — see spec
 * {@code docs/specs/006-converter-drift-detection.md}.
 */
final class ConverterDriftCheck {

	static final String DRIFT_PROPERTY = "scc.oa3.converter.drift";

	private static final Logger log = LoggerFactory.getLogger(ConverterDriftCheck.class);

	/**
	 * {@link OpenApiContractsVerifier} holds no mutable instance state — safe to share.
	 */
	private static final OpenApiContractsVerifier VERIFIER = new OpenApiContractsVerifier();

	private ConverterDriftCheck() {
		throw new AssertionError("Utility class");
	}

	/**
	 * Convenience overload for callers that have the spec only as a String. Used by tests
	 * and by callers that haven't already invoked the parser. Parsing goes through the
	 * safe parser (no remote {@code $ref} resolution).
	 */
	static void apply(@Nullable String openApiContent, Collection<Contract> contracts) {
		Mode mode = resolveMode();
		if (mode == Mode.OFF || contracts.isEmpty()) {
			return;
		}
		OpenApiVerificationReport report;
		try {
			report = VERIFIER.verifyInMemory(openApiContent != null ? openApiContent : "", contracts);
		}
		catch (RuntimeException ex) {
			log.error("Drift verification itself failed; failing fast.", ex);
			OpenApiVerificationReport synthetic = new OpenApiVerificationReport(List.of(new OpenApiContractViolation(
					Path.of("in-memory"), "drift-verification", "Drift verification crashed: " + ex.getMessage())));
			throw new OpenApiContractDriftException(synthetic, ex);
		}
		dispatch(mode, report);
	}

	static void apply(OpenAPI openAPI, Collection<Contract> contracts) {
		Mode mode = resolveMode();
		if (mode == Mode.OFF || contracts.isEmpty() || openAPI == null) {
			return;
		}

		OpenApiVerificationReport report;
		try {
			report = VERIFIER.verifyInMemory(openAPI, contracts);
		}
		catch (RuntimeException ex) {
			log.error("Drift verification itself failed; failing fast.", ex);
			OpenApiVerificationReport synthetic = new OpenApiVerificationReport(List.of(new OpenApiContractViolation(
					Path.of("in-memory"), "drift-verification", "Drift verification crashed: " + ex.getMessage())));
			throw new OpenApiContractDriftException(synthetic, ex);
		}

		dispatch(mode, report);
	}

	private static void dispatch(Mode mode, OpenApiVerificationReport report) {
		if (!report.hasViolations()) {
			return;
		}
		switch (mode) {
			case FAIL -> throw new OpenApiContractDriftException(report);
			case WARN -> log.warn("OpenAPI / contract drift detected:\n{}", report.render());
			case OFF -> {
				// unreachable
			}
		}
	}

	private static Mode resolveMode() {
		String raw = System.getProperty(DRIFT_PROPERTY);
		if (raw == null) {
			return Mode.FAIL;
		}
		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "fail", "" -> Mode.FAIL;
			case "warn" -> Mode.WARN;
			case "off" -> Mode.OFF;
			default -> {
				log.warn("Unknown {} value '{}', falling back to 'fail'.", DRIFT_PROPERTY, sanitizeForLog(normalized));
				yield Mode.FAIL;
			}
		};
	}

	private static String sanitizeForLog(String value) {
		String trimmed = value.length() > 64 ? value.substring(0, 64) : value;
		return trimmed.replace('\n', ' ').replace('\r', ' ');
	}

	private enum Mode {

		FAIL, WARN, OFF

	}

}
