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

import java.io.File;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

class Oa3Parser {

	OpenAPI parseOpenAPI(File file) {
		ParseOptions options = new ParseOptions();
		options.setResolve(false);
		options.setResolveFully(false);
		options.setResolveCombinators(false);
		options.setResolveRequestBody(false);
		options.setResolveResponses(false);
		SwaggerParseResult result = new OpenAPIV3Parser().readLocation(file.getPath(), null, options);
		OpenAPI spec = (result != null) ? result.getOpenAPI() : null;
		if (spec == null) {
			String details = (result != null && result.getMessages() != null && !result.getMessages().isEmpty())
					? " — " + String.join("; ", result.getMessages()) : "";
			throw new IllegalArgumentException(
					"OpenAPI specification %s could not be parsed%s".formatted(file.getPath(), details));
		}
		if (spec.getPaths() == null || spec.getPaths().isEmpty()) {
			throw new IllegalArgumentException("OpenAPI specification %s contains no paths".formatted(file.getPath()));
		}
		return spec;
	}

}
