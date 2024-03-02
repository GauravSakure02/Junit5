/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.launcher;

public class TestLauncherDiscoveryListener implements LauncherDiscoveryListener {
	public static boolean called;

	@Override
	public void launcherDiscoveryStarted(LauncherDiscoveryRequest request) {
		called = true;
	}
}
