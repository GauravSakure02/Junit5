/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.engine.extension;

import static org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.BOTTOM_UP;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePreDestroyCallback;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.StringUtils;
import org.junit.platform.engine.support.hierarchical.ThrowableCollector;

/**
 * {@code AutoCloseExtension} is a JUnit Jupiter extension that closes resources
 * if a field in a test class is annotated with {@link AutoClose @AutoClose}.
 *
 * <p>Consult the Javadoc for {@code @AutoClose} for details on the contract.
 *
 * @since 5.11
 * @see AutoClose
 */
class AutoCloseExtension implements TestInstancePreDestroyCallback, AfterAllCallback {

	private static final Logger logger = LoggerFactory.getLogger(AutoCloseExtension.class);

	@Override
	public void preDestroyTestInstance(ExtensionContext context) {
		ThrowableCollector throwableCollector = new ThrowableCollector(__ -> false);
		TestInstancePreDestroyCallback.preDestroyTestInstances(context,
			testInstance -> closeFields(testInstance.getClass(), testInstance, throwableCollector));
		throwableCollector.assertEmpty();
	}

	@Override
	public void afterAll(ExtensionContext context) {
		ThrowableCollector throwableCollector = new ThrowableCollector(__ -> false);
		closeFields(context.getRequiredTestClass(), null, throwableCollector);
		throwableCollector.assertEmpty();
	}

	private static void closeFields(Class<?> testClass, Object testInstance, ThrowableCollector throwableCollector) {
		Predicate<Field> predicate = (testInstance == null ? ReflectionUtils::isStatic : ReflectionUtils::isNotStatic);
		AnnotationUtils.findAnnotatedFields(testClass, AutoClose.class, predicate, BOTTOM_UP).forEach(
			field -> throwableCollector.execute(() -> closeField(field, testInstance)));
	}

	private static void closeField(Field field, Object testInstance) throws Exception {
		String methodName = AnnotationUtils.findAnnotation(field, AutoClose.class).get().value();
		Class<?> fieldType = field.getType();

		checkCondition(StringUtils.isNotBlank(methodName), "@AutoClose on field %s must specify a method name.", field);
		checkCondition(!fieldType.isPrimitive(), "@AutoClose is not supported on primitive field %s.", field);
		checkCondition(!fieldType.isArray(), "@AutoClose is not supported on array field %s.", field);

		Object fieldValue = ReflectionUtils.tryToReadFieldValue(field, testInstance).get();
		if (fieldValue == null) {
			logger.warn(() -> String.format("Cannot @AutoClose field %s because it is null.", getQualifiedName(field)));
		}
		else {
			invokeCloseMethod(field, fieldValue, methodName.trim());
		}
	}

	private static void invokeCloseMethod(Field field, Object target, String methodName) throws Exception {
		// Avoid reflection if we can directly invoke close() via AutoCloseable.
		if (target instanceof AutoCloseable && "close".equals(methodName)) {
			((AutoCloseable) target).close();
			return;
		}

		Class<?> targetType = target.getClass();
		Method closeMethod = ReflectionUtils.findMethod(targetType, methodName).orElseThrow(
			() -> new ExtensionConfigurationException(
				String.format("Cannot @AutoClose field %s because %s does not define method %s().",
					getQualifiedName(field), targetType.getName(), methodName)));

		closeMethod = ReflectionUtils.getInterfaceMethodIfPossible(closeMethod, targetType);
		ReflectionUtils.invokeMethod(closeMethod, target);
	}

	private static void checkCondition(boolean condition, String messageFormat, Field field) {
		Preconditions.condition(condition, () -> String.format(messageFormat, getQualifiedName(field)));
	}

	private static String getQualifiedName(Field field) {
		String typeName = field.getDeclaringClass().getCanonicalName();
		if (typeName == null) {
			typeName = field.getDeclaringClass().getTypeName();
		}
		return typeName + "." + field.getName();
	}

}
