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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the invariants of the package rename in
 * {@code docs/specs/007-package-rename.md}.
 */
class PackageRenameInvariantsTest {

	@Test
	void no_source_file_lives_under_the_old_org_springframework_packages() throws IOException {
		try (Stream<Path> walk = Files.walk(Path.of("src"))) {
			List<Path> stragglers = walk
				.filter(p -> p.toString().contains("/org/springframework/cloud/contract/verifier/"))
				.toList();
			assertThat(stragglers).as("old SCC-namespaced source directories must be deleted").isEmpty();
		}
	}

	@Test
	void spring_factories_does_not_register_our_converter() throws IOException {
		Path factories = Path.of("src/test/resources/META-INF/spring.factories");
		if (!Files.exists(factories)) {
			return;
		}
		String content = Files.readString(factories);
		assertThat(content).as("our converter must not be SPI-registered per spec 007 rule 4")
			.doesNotContain("OpenApiContractConverter");
	}

}
