/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.engine.execution;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.Optional;

import org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.ReflectiveInterceptorCall;

/**
 * Unit tests for {@link InterceptingExecutableInvoker}.
 *
 * @since 5.0
 */
class InterceptingExecutableInvokerTests extends AbstractExecutableInvokerTests {

	@Override
	void invokeMethod() {
		newInvoker().invoke(this.method, this.instance, this.extensionContext, this.extensionRegistry,
			passthroughInterceptor());
	}

	@Override
	<T> T invokeConstructor(Constructor<T> constructor, Object outerInstance) {
		return newInvoker().invoke(constructor, Optional.ofNullable(outerInstance), extensionContext, extensionRegistry,
			passthroughInterceptor());
	}

	private InterceptingExecutableInvoker newInvoker() {
		return new InterceptingExecutableInvoker();
	}

	private static <E extends Executable, T> ReflectiveInterceptorCall<E, T> passthroughInterceptor() {
		return (interceptor, invocation, invocationContext, extensionContext) -> invocation.proceed();
	}

}
