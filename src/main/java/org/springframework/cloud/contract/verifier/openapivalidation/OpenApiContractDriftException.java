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

/**
 * Thrown when contracts produced from an OpenAPI specification drift from the spec they
 * were generated from. Carries the full {@link OpenApiVerificationReport} so callers can
 * surface every violation, not just the first one.
 *
 * @since 0.2.0
 */
public class OpenApiContractDriftException extends RuntimeException {

	private final transient OpenApiVerificationReport report;

	public OpenApiContractDriftException(OpenApiVerificationReport report) {
		super(report.render());
		this.report = report;
	}

	public OpenApiVerificationReport report() {
		return this.report;
	}

}
