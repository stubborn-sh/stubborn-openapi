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

import java.util.Collection;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.verifier.openapivalidation.OpenApiContractDriftException;
import org.springframework.cloud.contract.verifier.openapivalidation.OpenApiContractsVerifier;
import org.springframework.cloud.contract.verifier.openapivalidation.OpenApiVerificationReport;

/**
 * Verifies contracts produced by {@link OpenApiContractConverter} against the OpenAPI
 * specification they were generated from. Behaviour is controlled by the
 * {@code scc.oa3.converter.drift} system property — see spec
 * {@code docs/specs/006-converter-drift-detection.md}.
 */
final class ConverterDriftCheck {

	static final String DRIFT_PROPERTY = "scc.oa3.converter.drift";

	private static final Logger log = LoggerFactory.getLogger(ConverterDriftCheck.class);

	private static final OpenApiContractsVerifier VERIFIER = new OpenApiContractsVerifier();

	private ConverterDriftCheck() {
		throw new AssertionError("Utility class");
	}

	static void apply(String openApiContent, Collection<Contract> contracts) {
		Mode mode = resolveMode();
		if (mode == Mode.OFF || contracts.isEmpty()) {
			return;
		}

		OpenApiVerificationReport report;
		try {
			report = VERIFIER.verifyInMemory(openApiContent, contracts);
		}
		catch (RuntimeException ex) {
			log.error("Drift verification itself failed; failing fast.", ex);
			throw ex;
		}

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
				log.warn("Unknown {} value '{}', falling back to 'fail'.", DRIFT_PROPERTY, raw);
				yield Mode.FAIL;
			}
		};
	}

	private enum Mode {

		FAIL, WARN, OFF

	}

}
