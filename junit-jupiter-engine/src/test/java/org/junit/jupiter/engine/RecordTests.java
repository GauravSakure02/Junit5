/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class RecordTests extends AbstractJupiterTestEngineTests {

	@Test
	void recordsAreTestClasses() {
		executeTestsForClass(TestCase.class).testEvents() //
				.assertStatistics(stats -> stats.finished(2).succeeded(1).failed(1));
	}

	record TestCase() {

		@Test
		void succeedingTest() {
			assertTrue(true);
		}

		@Test
		void failingTest() {
			fail("always fails");
		}
	}
}
