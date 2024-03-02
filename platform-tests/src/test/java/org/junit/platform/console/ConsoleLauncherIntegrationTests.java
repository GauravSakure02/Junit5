/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @since 1.0
 */
class ConsoleLauncherIntegrationTests {

	@Test
	void executeWithoutArgumentsFailsAndPrintsHelpInformation() {
		var result = new ConsoleLauncherWrapper().execute(-1);
		assertAll("empty args array results in display of help information and an exception stacktrace", //
			() -> assertThat(result.err).contains("help information"), //
			() -> assertThat(result.err).contains(
				"Please specify an explicit selector option or use --scan-class-path or --scan-modules") //
		);
	}

	@ParameterizedTest
	@ValueSource(strings = { //
			"-e junit-jupiter -p org.junit.platform.console.subpackage", //
			"execute -e junit-jupiter -p org.junit.platform.console.subpackage" //
	})
	void executeWithoutExcludeClassnameOptionDoesNotExcludeClassesAndMustIncludeAllClassesMatchingTheStandardClassnamePattern(
			final String line) {
		String[] args = line.split(" ");
		assertEquals(9, new ConsoleLauncherWrapper().execute(args).getTestsFoundCount());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"-e junit-jupiter -p org.junit.platform.console.subpackage --exclude-classname"
					+ " ^org\\.junit\\.platform\\.console\\.subpackage\\..*",
			"execute -e junit-jupiter -p org.junit.platform.console.subpackage --exclude-classname"
					+ " ^org\\.junit\\.platform\\.console\\.subpackage\\..*" //
	})
	void executeWithExcludeClassnameOptionExcludesClasses(final String line) {
		String[] args = line.split(" ");
		var result = new ConsoleLauncherWrapper().execute(args);
		assertAll("all subpackage test classes are excluded by the class name filter", //
			() -> assertArrayEquals(args, result.args), //
			() -> assertEquals(0, result.code), //
			() -> assertEquals(0, result.getTestsFoundCount()) //
		);
	}

	@ParameterizedTest
	@ValueSource(strings = { //
			"-e junit-jupiter -o java.base", "-e junit-jupiter --select-module java.base", //
			"execute -e junit-jupiter -o java.base", "execute -e junit-jupiter --select-module java.base" //
	})
	void executeSelectingModuleNames(final String line) {
		String[] args1 = line.split(" ");
		assertEquals(0, new ConsoleLauncherWrapper().execute(args1).getTestsFoundCount());
	}

	@ParameterizedTest
	@ValueSource(strings = { "-e junit-jupiter --scan-modules", "execute -e junit-jupiter --scan-modules" })
	void executeScanModules(final String line) {
		String[] args1 = line.split(" ");
		assertEquals(0, new ConsoleLauncherWrapper().execute(args1).getTestsFoundCount());
	}

}
