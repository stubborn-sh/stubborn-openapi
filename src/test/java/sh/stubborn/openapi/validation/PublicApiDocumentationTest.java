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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts every public class, public method, and public constructor across the library's
 * main sources carries a Javadoc block — the documentation contract from
 * {@code docs/specs/006-converter-drift-detection.md}.
 */
class PublicApiDocumentationTest {

	private static final Path SRC_MAIN = Path.of("src", "main", "java");

	/**
	 * Finds the offset of the first non-whitespace character preceding {@code idx} — used
	 * to inspect what immediately precedes a {@code public} declaration.
	 */
	private static int previousNonWhitespace(String source, int idx) {
		for (int i = idx - 1; i >= 0; i--) {
			if (!Character.isWhitespace(source.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	@Test
	void every_public_declaration_in_main_sources_has_a_javadoc_block() throws IOException {
		Pattern publicDecl = Pattern.compile("(?m)^\\s*public\\s");
		try (Stream<Path> sources = Files.walk(SRC_MAIN)) {
			List<String> failures = new java.util.ArrayList<>();
			sources.filter(p -> p.toString().endsWith(".java"))
				.filter(p -> !p.endsWith("package-info.java"))
				.forEach(p -> {
					String src;
					try {
						src = Files.readString(p, StandardCharsets.UTF_8);
					}
					catch (IOException ex) {
						throw new RuntimeException(ex);
					}
					var matcher = publicDecl.matcher(src);
					while (matcher.find()) {
						int start = matcher.start();
						int prev = lastTokenBeforeAnnotations(src, start);
						boolean closesJavadoc = prev > 0 && src.charAt(prev) == '/' && src.charAt(prev - 1) == '*';
						if (!closesJavadoc) {
							failures.add(p + ":" + lineOf(src, start) + " — public declaration without Javadoc");
						}
					}
				});
			assertThat(failures).as("every public declaration must carry Javadoc per docs/specs/006").isEmpty();
		}
	}

	/**
	 * Walks backward from {@code idx}, skipping any whole lines whose first
	 * non-whitespace character is {@code @} (annotations), and returns the index of the
	 * last non-whitespace character before that block — or -1 if no such char exists.
	 */
	private static int lastTokenBeforeAnnotations(String src, int idx) {
		int cursor = idx - 1;
		while (cursor >= 0) {
			while (cursor >= 0 && Character.isWhitespace(src.charAt(cursor))) {
				cursor--;
			}
			if (cursor < 0) {
				return -1;
			}
			int lineStart = cursor;
			while (lineStart > 0 && src.charAt(lineStart - 1) != '\n') {
				lineStart--;
			}
			int firstNonWs = lineStart;
			while (firstNonWs <= cursor && Character.isWhitespace(src.charAt(firstNonWs))) {
				firstNonWs++;
			}
			if (firstNonWs <= cursor && src.charAt(firstNonWs) == '@') {
				cursor = lineStart - 1;
				continue;
			}
			return cursor;
		}
		return -1;
	}

	private static int lineOf(String src, int idx) {
		int line = 1;
		for (int i = 0; i < idx && i < src.length(); i++) {
			if (src.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

}
