/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.vintage.engine.samples.junit4;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @since 4.12
 */
@Label("(TestClass)")
@RunWith(RunnerWithCustomUniqueIdsAndDisplayNames.class)
public class JUnit4TestCaseWithRunnerWithCustomUniqueIdsAndDisplayNames {

	@Test
	@Label("(TestMethod)")
	public void test() {
		Assert.fail();
	}

}

@Retention(RUNTIME)
@interface Label {
	String value();
}
