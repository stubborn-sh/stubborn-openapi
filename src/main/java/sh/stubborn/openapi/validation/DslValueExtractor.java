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
package sh.stubborn.openapi.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.cloud.contract.spec.internal.DslProperty;
import org.springframework.cloud.contract.spec.internal.NotToEscapePattern;
import org.springframework.cloud.contract.spec.internal.RegexProperty;

/**
 * Recursively unwraps Spring Cloud Contract {@link DslProperty} values into plain Java
 * values that can be JSON-serialized for schema validation. Always prefers the
 * <em>server</em> side of a two-sided property — that is what the stub will emit and is
 * therefore what must conform to the OpenAPI schema.
 *
 * <p>
 * SCC helpers like {@code contentType()} / {@code accept()} / {@code matching()} store a
 * regex on one side (typically the producer-side {@link Pattern} or
 * {@link NotToEscapePattern}) and a literal on the other. For schema validation the
 * literal is always preferred — type-checked only against SCC's regex marker types and
 * {@link Pattern}, never via fragile string-content heuristics.
 */
final class DslValueExtractor {

	private DslValueExtractor() {
		throw new AssertionError("Utility class");
	}

	@Nullable static Object unwrap(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof DslProperty<?> dsl) {
			Object server = dsl.getServerValue();
			Object client = dsl.getClientValue();
			Object serverResolved = (server != null && server != value) ? unwrap(server) : null;
			Object clientResolved = (client != null && client != value) ? unwrap(client) : null;
			if (serverResolved != null && !isRegexMarker(serverResolved)) {
				return serverResolved;
			}
			if (clientResolved != null && !isRegexMarker(clientResolved)) {
				return clientResolved;
			}
			return (serverResolved != null) ? serverResolved : clientResolved;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) {
				// Null map values are intentionally retained — Jackson serialises them as
				// JSON null and many schemas allow nullable fields.
				out.put(String.valueOf(e.getKey()), unwrap(e.getValue()));
			}
			return out;
		}
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>(list.size());
			for (Object item : list) {
				out.add(unwrap(item));
			}
			return out;
		}
		return value;
	}

	/**
	 * Type-only check — never inspects string content. SCC carries regex intent through
	 * concrete types, so a literal string like {@code "SKU[0-9]X"} is correctly treated
	 * as a literal even though it contains regex-shaped characters.
	 */
	private static boolean isRegexMarker(Object value) {
		return value instanceof Pattern || value instanceof RegexProperty || value instanceof NotToEscapePattern;
	}

}
