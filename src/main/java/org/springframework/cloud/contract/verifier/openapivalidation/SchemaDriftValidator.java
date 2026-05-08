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
import java.util.regex.Pattern;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReport.Level;
import com.atlassian.oai.validator.report.ValidationReport.Message;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.contract.spec.Contract;

/**
 * Validates a {@link Contract} against an OpenAPI document at the schema level —
 * request/response bodies, headers, query/path parameters, and content types — using the
 * Atlassian {@code swagger-request-validator}.
 */
final class SchemaDriftValidator {

	private static final Logger log = LoggerFactory.getLogger(SchemaDriftValidator.class);

	/**
	 * A path containing {@code [}, {@code (}, or a backslash is treated as carrying a
	 * regex matcher per spec rule 11. Suppression then only applies to messages whose
	 * {@code MessageContext.getParameter().in == "path"} — never to query / header / body
	 * / content-type messages, even on the same path.
	 */
	private static final Pattern REGEX_PATH_HINT = Pattern.compile(".*[\\[\\\\(].*");

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
			boolean pathLooksLikeRegex = REGEX_PATH_HINT.matcher(path).matches();
			for (Message msg : report.getMessages()) {
				if (msg.getLevel() != Level.ERROR) {
					continue;
				}
				if (pathLooksLikeRegex && isPathParameterValidation(msg)) {
					// Spec rule 11: regex-matcher path-param values are not type-checked.
					// Narrow suppression — query/header/body/content-type errors on the
					// same path are still reported.
					continue;
				}
				violations
					.add(new OpenApiContractViolation(sourcePath, contractName, "Schema drift: " + msg.getMessage()));
			}
		}
		catch (RuntimeException ex) {
			log.warn("Schema drift validation failed for {}", contractName, ex);
			violations.add(new OpenApiContractViolation(sourcePath, contractName,
					"Schema drift validation failed: " + ex.getMessage()));
		}
	}

	private static boolean isPathParameterValidation(Message msg) {
		return msg.getContext()
			.flatMap(ctx -> ctx.getParameter())
			.map(Parameter::getIn)
			.map(in -> "path".equalsIgnoreCase(in))
			.orElse(false);
	}

}
