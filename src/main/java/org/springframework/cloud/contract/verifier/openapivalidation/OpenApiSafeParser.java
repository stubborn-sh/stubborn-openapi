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

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.jspecify.annotations.Nullable;

/**
 * Parses OpenAPI documents with remote {@code $ref} resolution disabled. Mitigates SSRF —
 * a malicious spec containing {@code $ref: 'https://attacker.invalid/x'} cannot make the
 * build JVM open outbound connections. Local {@code #/components} references are still
 * consumed by the Atlassian validator at validation time, so disabling parser-side
 * resolution does not affect schema-level drift checks.
 */
final class OpenApiSafeParser {

	private OpenApiSafeParser() {
		throw new AssertionError("Utility class");
	}

	static ParseOptions safeOptions() {
		ParseOptions options = new ParseOptions();
		options.setResolve(false);
		options.setResolveFully(false);
		options.setResolveCombinators(false);
		options.setResolveRequestBody(false);
		options.setResolveResponses(false);
		return options;
	}

	@Nullable static OpenAPI parsePath(String path) {
		SwaggerParseResult result = new OpenAPIV3Parser().readLocation(path, null, safeOptions());
		return result != null ? result.getOpenAPI() : null;
	}

	@Nullable static OpenAPI parseContents(String content) {
		SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, safeOptions());
		return result != null ? result.getOpenAPI() : null;
	}

}
