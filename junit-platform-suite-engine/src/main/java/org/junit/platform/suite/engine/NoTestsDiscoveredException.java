/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.suite.engine;

import org.junit.platform.commons.JUnitException;

class NoTestsDiscoveredException extends JUnitException {

	private static final long serialVersionUID = 1L;

	NoTestsDiscoveredException(Class<?> suiteClass) {
		super(String.format("Suite [%s] did not discover any tests", suiteClass.getName()));
	}

}
