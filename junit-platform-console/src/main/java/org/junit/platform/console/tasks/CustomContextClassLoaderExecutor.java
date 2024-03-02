/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.console.tasks;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.platform.commons.JUnitException;

/**
 * @since 1.0
 */
class CustomContextClassLoaderExecutor {

	private final Optional<ClassLoader> customClassLoader;

	CustomContextClassLoaderExecutor(Optional<ClassLoader> customClassLoader) {
		this.customClassLoader = customClassLoader;
	}

	<T> T invoke(Supplier<T> supplier) {
		if (customClassLoader.isPresent()) {
			// Only get/set context class loader when necessary to prevent problems with
			// security managers
			return replaceThreadContextClassLoaderAndInvoke(customClassLoader.get(), supplier);
		}
		return supplier.get();
	}

	private <T> T replaceThreadContextClassLoaderAndInvoke(ClassLoader customClassLoader, Supplier<T> supplier) {
		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(customClassLoader);
			return supplier.get();
		}
		finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
			if (customClassLoader instanceof AutoCloseable) {
				close((AutoCloseable) customClassLoader);
			}
		}
	}

	private static void close(AutoCloseable customClassLoader) {
		try {
			customClassLoader.close();
		}
		catch (Exception e) {
			throw new JUnitException("Failed to close custom class loader", e);
		}
	}

}
