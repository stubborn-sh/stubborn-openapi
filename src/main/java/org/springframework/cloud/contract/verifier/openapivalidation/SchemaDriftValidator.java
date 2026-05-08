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

import java.nio.file.Path;
import java.util.List;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReport.Level;
import com.atlassian.oai.validator.report.ValidationReport.Message;
import io.swagger.v3.oas.models.OpenAPI;
import org.jspecify.annotations.Nullable;

import org.springframework.cloud.contract.spec.Contract;

/**
 * Validates a {@link Contract} against an OpenAPI document at the schema level —
 * request/response bodies, headers, query/path parameters, and content types — using the
 * Atlassian {@code swagger-request-validator}.
 */
final class SchemaDriftValidator {

	@Nullable private final OpenApiInteractionValidator validator;

	SchemaDriftValidator(@Nullable OpenAPI openAPI) {
		this.validator = (openAPI != null) ? OpenApiInteractionValidator.createFor(openAPI).build() : null;
	}

	void validate(Contract contract, String method, String path, int status, Path sourcePath, String contractName,
			List<OpenApiContractViolation> violations) {
		if (this.validator == null) {
			return;
		}
		try {
			SimpleRequest req = ContractHttpAdapter.toRequest(contract, method, path);
			SimpleResponse resp = ContractHttpAdapter.toResponse(contract, status);
			ValidationReport report = this.validator.validate(req, resp);
			for (Message msg : report.getMessages()) {
				if (msg.getLevel() == Level.ERROR) {
					violations.add(new OpenApiContractViolation(sourcePath, contractName,
							"Schema drift: " + msg.getMessage()));
				}
			}
		}
		catch (RuntimeException ex) {
			violations.add(new OpenApiContractViolation(sourcePath, contractName,
					"Schema drift validation failed: " + ex.getMessage()));
		}
	}

}
