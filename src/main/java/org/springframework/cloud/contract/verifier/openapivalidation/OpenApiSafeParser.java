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

import java.util.List;

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
 *
 * <p>
 * Parse-error messages from {@link SwaggerParseResult#getMessages()} are surfaced via
 * {@link Result#messages()} so callers can present them as violations rather than
 * silently dropping them and reporting a misleading "no paths" error.
 */
public final class OpenApiSafeParser {

	private OpenApiSafeParser() {
		throw new AssertionError("Utility class");
	}

	/**
	 * Returns the {@link ParseOptions} used by every safe parse call — all
	 * resolution-related flags are disabled to block remote {@code $ref} traversal at
	 * parse time.
	 * @return a fresh, mutable {@link ParseOptions} with the SSRF-mitigation flags set
	 * @since 0.2.0
	 */
	public static ParseOptions safeOptions() {
		ParseOptions options = new ParseOptions();
		options.setResolve(false);
		options.setResolveFully(false);
		options.setResolveCombinators(false);
		options.setResolveRequestBody(false);
		options.setResolveResponses(false);
		return options;
	}

	/**
	 * Parses an OpenAPI document from a filesystem path or URL with remote {@code $ref}
	 * resolution disabled.
	 * @param path the file path or URL the parser should read
	 * @return a {@link Result} carrying the parsed model (possibly null) and any
	 * parse-error messages produced by the parser
	 * @since 0.2.0
	 */
	public static Result parsePath(String path) {
		SwaggerParseResult result = new OpenAPIV3Parser().readLocation(path, null, safeOptions());
		return adapt(result);
	}

	/**
	 * Parses an OpenAPI document from an in-memory string with remote {@code $ref}
	 * resolution disabled.
	 * @param content the raw OpenAPI YAML or JSON content
	 * @return a {@link Result} carrying the parsed model (possibly null) and any
	 * parse-error messages produced by the parser
	 * @since 0.2.0
	 */
	public static Result parseContents(String content) {
		SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, safeOptions());
		return adapt(result);
	}

	private static Result adapt(@Nullable SwaggerParseResult result) {
		if (result == null) {
			return new Result(null, List.of());
		}
		List<String> messages = result.getMessages() != null ? List.copyOf(result.getMessages()) : List.of();
		return new Result(result.getOpenAPI(), messages);
	}

	/**
	 * The outcome of a safe parse — the (possibly null) {@link OpenAPI} model plus any
	 * messages emitted by the parser. Callers should surface or log those messages rather
	 * than dropping them silently.
	 *
	 * @param openAPI the parsed OpenAPI model, or {@code null} if parsing failed
	 * @param messages diagnostic messages emitted by the parser; never {@code null}
	 * @since 0.2.0
	 */
	public record Result(@Nullable OpenAPI openAPI, List<String> messages) {
	}

}
