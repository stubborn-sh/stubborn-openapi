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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.cloud.contract.spec.internal.DslProperty;

/**
 * Recursively unwraps Spring Cloud Contract {@link DslProperty} values into plain Java
 * values that can be JSON-serialized for schema validation. Always prefers the
 * <em>server</em> side of a two-sided property — that is what the stub will emit and is
 * therefore what must conform to the OpenAPI schema.
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
			// SCC helpers like contentType()/accept() store a regex on one side and a
			// literal on the other. Schema validation needs the literal — pick the
			// non-regex side when the choice is between them. Resolve each side fully
			// before checking, so wrappers (ServerDslProperty → NotToEscapePattern →
			// Pattern) collapse to their terminal value.
			Object serverResolved = (server != null && server != value) ? unwrap(server) : null;
			Object clientResolved = (client != null && client != value) ? unwrap(client) : null;
			if (serverResolved != null && !looksLikeRegex(serverResolved)) {
				return serverResolved;
			}
			if (clientResolved != null && !looksLikeRegex(clientResolved)) {
				return clientResolved;
			}
			return (serverResolved != null) ? serverResolved : clientResolved;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) {
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

	private static boolean looksLikeRegex(Object value) {
		if (value instanceof Pattern) {
			return true;
		}
		if (!(value instanceof CharSequence cs)) {
			return false;
		}
		String s = cs.toString();
		return s.contains(".*") || s.contains(".+") || s.contains("\\d") || s.contains("[0-9]") || s.contains("[a-z]")
				|| s.contains("[A-Z]");
	}

}
