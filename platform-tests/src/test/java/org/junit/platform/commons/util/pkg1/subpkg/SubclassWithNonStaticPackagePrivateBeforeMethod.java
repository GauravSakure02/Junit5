/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.commons.util.pkg1.subpkg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.commons.util.pkg1.SuperclassWithStaticPackagePrivateBeforeMethod;

/**
 * @see https://github.com/junit-team/junit5/issues/3553
 */
public class SubclassWithNonStaticPackagePrivateBeforeMethod extends SuperclassWithStaticPackagePrivateBeforeMethod {

	@BeforeEach
	void before() {
		// no-op
	}

}
