/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.params.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.engine.extension.MutableExtensionRegistry.createRegistryWithDefaultExtensions;
import static org.junit.platform.commons.util.ReflectionUtils.findMethod;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.engine.execution.DefaultExecutableInvoker;
import org.junit.jupiter.engine.extension.MutableExtensionRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.commons.PreconditionViolationException;
import org.junit.platform.commons.test.TestClassLoader;
import org.junit.platform.commons.util.ReflectionUtils;

/**
 * @since 5.0
 */
class MethodArgumentsProviderTests {

	private MutableExtensionRegistry extensionRegistry;

	@Test
	void providesArgumentsUsingStream() {
		var arguments = provideArguments("stringStreamProvider");

		assertThat(arguments).containsExactly(array("foo"), array("bar"));
	}

	@Test
	void providesArgumentsUsingDoubleStream() {
		var arguments = provideArguments("doubleStreamProvider");

		assertThat(arguments).containsExactly(array(1.2), array(3.4));
	}

	@Test
	void providesArgumentsUsingLongStream() {
		var arguments = provideArguments("longStreamProvider");

		assertThat(arguments).containsExactly(array(1L), array(2L));
	}

	@Test
	void providesArgumentsUsingIntStream() {
		var arguments = provideArguments("intStreamProvider");

		assertThat(arguments).containsExactly(array(1), array(2));
	}

	/**
	 * @since 5.3.2
	 */
	@Test
	void providesArgumentsUsingStreamOfIntArrays() {
		var arguments = provideArguments("intArrayStreamProvider");

		assertThat(arguments).containsExactly( //
			new Object[] { new int[] { 1, 2 } }, //
			new Object[] { new int[] { 3, 4 } } //
		);
	}

	/**
	 * @since 5.3.2
	 */
	@Test
	void providesArgumentsUsingStreamOfTwoDimensionalIntArrays() {
		var arguments = provideArguments("twoDimensionalIntArrayStreamProvider");

		assertThat(arguments).containsExactly( //
			array((Object) new int[][] { { 1, 2 }, { 2, 3 } }), //
			array((Object) new int[][] { { 4, 5 }, { 5, 6 } }) //
		);
	}

	@Test
	void providesArgumentsUsingStreamOfObjectArrays() {
		var arguments = provideArguments("objectArrayStreamProvider");

		assertThat(arguments).containsExactly(array("foo", 42), array("bar", 23));
	}

	/**
	 * @since 5.3.2
	 */
	@Test
	void providesArgumentsUsingStreamOfTwoDimensionalObjectArrays() {
		var arguments = provideArguments("twoDimensionalObjectArrayStreamProvider");

		assertThat(arguments).containsExactly( //
			array((Object) array(array("a", 1), array("b", 2))), //
			array((Object) array(array("c", 3), array("d", 4))) //
		);
	}

	@Test
	void providesArgumentsUsingStreamOfArguments() {
		var arguments = provideArguments("argumentsStreamProvider");

		assertThat(arguments).containsExactly(array("foo", 42), array("bar", 23));
	}

	@Test
	void providesArgumentsUsingIterable() {
		var arguments = provideArguments("stringIterableProvider");

		assertThat(arguments).containsExactly(array("foo"), array("bar"));
	}

	@Test
	void providesArgumentsUsingIterator() {
		var arguments = provideArguments("stringIteratorProvider");

		assertThat(arguments).containsExactly(array("foo"), array("bar"));
	}

	@Test
	void providesArgumentsUsingMultipleFactoryMethods() {
		var arguments = provideArguments("stringStreamProvider", "stringIterableProvider");

		assertThat(arguments).containsExactly(array("foo"), array("bar"), array("foo"), array("bar"));
	}

	@Test
	void providesArgumentsUsingIterableOfObjectArrays() {
		var arguments = provideArguments("objectArrayIterableProvider");

		assertThat(arguments).containsExactly(array("foo", 42), array("bar", 23));
	}

	@Test
	void providesArgumentsUsingListOfStrings() {
		var arguments = provideArguments("stringArrayListProvider");

		assertThat(arguments).containsExactly(array("foo"), array("bar"));
	}

	@Test
	void providesArgumentsUsingListOfObjectArrays() {
		var arguments = provideArguments("objectArrayListProvider");

		assertThat(arguments).containsExactly(array("foo", 42), array("bar", 23));
	}

	@Test
	void throwsExceptionWhenNonStaticLocalFactoryMethodIsReferencedWithLifecyclePerMethodSemantics() {
		var lifecyclePerClass = false;
		var exception = assertThrows(PreconditionViolationException.class,
			() -> provideArguments(NonStaticTestCase.class, lifecyclePerClass,
				"nonStaticStringStreamProvider").toArray());

		assertStaticIsRequired(exception);
	}

	@Test
	void throwsExceptionWhenNonStaticExternalFactoryMethodIsReferencedWithLifecyclePerMethodSemantics() {
		var factoryClass = NonStaticTestCase.class.getName();
		var factoryMethod = factoryClass + "#nonStaticStringStreamProvider";
		var lifecyclePerClass = false;
		var exception = assertThrows(PreconditionViolationException.class,
			() -> provideArguments(TestCase.class, lifecyclePerClass, factoryMethod).toArray());

		assertStaticIsRequired(exception);
	}

	@Test
	void throwsExceptionWhenNonStaticExternalFactoryMethodIsReferencedWithLifecyclePerClassSemantics() {
		var factoryClass = NonStaticTestCase.class.getName();
		var factoryMethod = factoryClass + "#nonStaticStringStreamProvider";
		boolean lifecyclePerClass = true;
		var exception = assertThrows(PreconditionViolationException.class,
			() -> provideArguments(TestCase.class, lifecyclePerClass, factoryMethod).toArray());

		assertStaticIsRequired(exception);
	}

	private static void assertStaticIsRequired(PreconditionViolationException exception) {
		assertThat(exception).hasMessageContainingAll("Method '",
			"' must be static: local factory methods must be static ",
			"unless the PER_CLASS @TestInstance lifecycle mode is used; ",
			"external factory methods must always be static.");
	}

	@Test
	void providesArgumentsFromNonStaticFactoryMethodWhenStaticIsNotRequired() {
		var arguments = provideArguments(NonStaticTestCase.class, true, "nonStaticStringStreamProvider");

		assertThat(arguments).containsExactly(array("foo"), array("bar"));
	}

	@Test
	void providesArgumentsUsingDefaultFactoryMethodName() {
		var testClass = DefaultFactoryMethodNameTestCase.class;
		var methodName = "testDefaultFactoryMethodName";
		var testMethod = findMethod(testClass, methodName, String.class).get();

		var arguments = provideArguments(testClass, testMethod, false, "");

		assertThat(arguments).containsExactly(array("foo"), array("bar"));
	}

	@Test
	void providesArgumentsUsingExternalFactoryMethod() {
		var arguments = provideArguments(ExternalFactoryMethods.class.getName() + "#stringsProvider");

		assertThat(arguments).containsExactly(array("string1"), array("string2"));
	}

	@Test
	void providesArgumentsUsingExternalFactoryMethodInTypeFromDifferentClassLoader() throws Exception {
		try (var testClassLoader = TestClassLoader.forClasses(TestCase.class, ExternalFactoryMethods.class)) {
			var testClass = testClassLoader.loadClass(TestCase.class.getName());
			var testMethod = ReflectionUtils.findMethod(testClass, "test").get();
			var fullyQualifiedMethodName = ExternalFactoryMethods.class.getName() + "#stringsProvider";

			assertThat(testClass.getClassLoader()).isSameAs(testClassLoader);

			var arguments = provideArguments(testClass, false, fullyQualifiedMethodName);
			assertThat(arguments).containsExactly(array("string1"), array("string2"));

			var factoryMethod = MethodArgumentsProvider.findFactoryMethodByFullyQualifiedName(testClass, testMethod,
				fullyQualifiedMethodName);
			assertThat(factoryMethod).isNotNull();
			assertThat(factoryMethod.getName()).isEqualTo("stringsProvider");
			assertThat(factoryMethod.getParameterTypes()).isEmpty();

			var declaringClass = factoryMethod.getDeclaringClass();
			assertThat(declaringClass.getName()).isEqualTo(ExternalFactoryMethods.class.getName());
			assertThat(declaringClass).isNotEqualTo(ExternalFactoryMethods.class);
			assertThat(declaringClass.getClassLoader()).isSameAs(testClassLoader);
		}
	}

	@Test
	void providesArgumentsUsingExternalFactoryMethodWithParentheses() {
		var arguments = provideArguments(ExternalFactoryMethods.class.getName() + "#stringsProvider()");

		assertThat(arguments).containsExactly(array("string1"), array("string2"));
	}

	@Test
	void providesArgumentsUsingExternalFactoryMethodFromStaticNestedClass() {
		var arguments = provideArguments(ExternalFactoryMethods.class.getName() + "$Nested#stringsProvider()");

		assertThat(arguments).containsExactly(array("nested string1"), array("nested string2"));
	}

	@Test
	void providesArgumentsUsingExternalAndInternalFactoryMethodsCombined() {
		var arguments = provideArguments("stringStreamProvider",
			ExternalFactoryMethods.class.getName() + "#stringsProvider");

		assertThat(arguments).containsExactly(array("foo"), array("bar"), array("string1"), array("string2"));
	}

	@Nested
	class PrimitiveArrays {

		@Test
		void providesArgumentsUsingBooleanArray() {
			var arguments = provideArguments("booleanArrayProvider");

			assertThat(arguments).containsExactly(array(Boolean.TRUE), array(Boolean.FALSE));
		}

		@Test
		void providesArgumentsUsingByteArray() {
			var arguments = provideArguments("byteArrayProvider");

			assertThat(arguments).containsExactly(array((byte) 1), array(Byte.MIN_VALUE));
		}

		@Test
		void providesArgumentsUsingCharArray() {
			var arguments = provideArguments("charArrayProvider");

			assertThat(arguments).containsExactly(array((char) 1), array(Character.MIN_VALUE));
		}

		@Test
		void providesArgumentsUsingDoubleArray() {
			var arguments = provideArguments("doubleArrayProvider");

			assertThat(arguments).containsExactly(array(1d), array(Double.MIN_VALUE));
		}

		@Test
		void providesArgumentsUsingFloatArray() {
			var arguments = provideArguments("floatArrayProvider");

			assertThat(arguments).containsExactly(array(1f), array(Float.MIN_VALUE));
		}

		@Test
		void providesArgumentsUsingIntArray() {
			var arguments = provideArguments("intArrayProvider");

			assertThat(arguments).containsExactly(array(47), array(Integer.MIN_VALUE));
		}

		@Test
		void providesArgumentsUsingLongArray() {
			var arguments = provideArguments("longArrayProvider");

			assertThat(arguments).containsExactly(array(47L), array(Long.MIN_VALUE));
		}

		@Test
		void providesArgumentsUsingShortArray() {
			var arguments = provideArguments("shortArrayProvider");

			assertThat(arguments).containsExactly(array((short) 47), array(Short.MIN_VALUE));
		}

	}

	@Nested
	class ObjectArrays {

		@Test
		void providesArgumentsUsingObjectArray() {
			var arguments = provideArguments("objectArrayProvider");

			assertThat(arguments).containsExactly(array(42), array("bar"));
		}

		@Test
		void providesArgumentsUsingStringArray() {
			var arguments = provideArguments("stringArrayProvider");

			assertThat(arguments).containsExactly(array("foo"), array("bar"));
		}

		@Test
		void providesArgumentsUsing2dObjectArray() {
			var arguments = provideArguments("twoDimensionalObjectArrayProvider");

			assertThat(arguments).containsExactly(array("foo", 42), array("bar", 23));
		}

	}

	@Nested
	class ParameterResolution {

		private final Method testMethod = findMethod(TestCase.class, "test").get();

		@BeforeEach
		void registerParameterResolver() {
			extensionRegistry = createRegistryWithDefaultExtensions(mock());
			extensionRegistry.registerExtension(StringResolver.class);
			extensionRegistry.registerExtension(StringArrayResolver.class);
			extensionRegistry.registerExtension(IntArrayResolver.class);
		}

		@Test
		void providesArgumentsInferringDefaultFactoryMethodThatAcceptsArgument() {
			Method testMethod = findMethod(TestCase.class, "overloadedStringStreamProvider", Object.class).get();
			String factoryMethodName = ""; // signals to use default
			var arguments = provideArguments(testMethod, factoryMethodName);

			assertThat(arguments).containsExactly(array("foo!"), array("bar!"));
		}

		@Test
		void providesArgumentsUsingSimpleNameForFactoryMethodThatAcceptsArgumentWithoutSpecifyingParameterList() {
			var arguments = provideArguments("stringStreamProviderWithParameter");

			assertThat(arguments).containsExactly(array("foo!"), array("bar!"));
		}

		@Test
		void providesArgumentsUsingFullyQualifiedNameForFactoryMethodThatAcceptsArgumentWithoutSpecifyingParameterList() {
			var arguments = provideArguments(TestCase.class.getName() + "#stringStreamProviderWithParameter");

			assertThat(arguments).containsExactly(array("foo!"), array("bar!"));
		}

		@Test
		void providesArgumentsUsingFullyQualifiedNameSpecifyingParameter() {
			var arguments = provideArguments(
				TestCase.class.getName() + "#stringStreamProviderWithParameter(java.lang.String)");

			assertThat(arguments).containsExactly(array("foo!"), array("bar!"));
		}

		@Test
		void providesArgumentsUsingLocalQualifiedNameSpecifyingParameter() {
			var arguments = provideArguments(testMethod, "stringStreamProviderWithParameter(java.lang.String)");

			assertThat(arguments).containsExactly(array("foo!"), array("bar!"));
		}

		@Test
		void providesArgumentsUsingFullyQualifiedNameForOverloadedFactoryMethodSpecifyingEmptyParameterList() {
			var arguments = provideArguments(
				TestCase.class.getName() + "#stringStreamProviderWithOrWithoutParameter()");

			assertThat(arguments).containsExactly(array("foo"), array("bar"));
		}

		@Test
		void providesArgumentsUsingLocalQualifiedNameForOverloadedFactoryMethodSpecifyingEmptyParameterList() {
			var arguments = provideArguments(this.testMethod, "stringStreamProviderWithOrWithoutParameter()");

			assertThat(arguments).containsExactly(array("foo"), array("bar"));
		}

		@Test
		void providesArgumentsUsingFullyQualifiedNameForOverloadedFactoryMethodSpecifyingParameter() {
			var arguments = provideArguments(
				TestCase.class.getName() + "#stringStreamProviderWithOrWithoutParameter(java.lang.String)");

			assertThat(arguments).containsExactly(array("foo!"), array("bar!"));
		}

		@Test
		void providesArgumentsUsingLocalQualifiedNameForOverloadedFactoryMethodSpecifyingParameter() {
			var arguments = provideArguments(testMethod,
				"stringStreamProviderWithOrWithoutParameter(java.lang.String)");

			assertThat(arguments).containsExactly(array("foo!"), array("bar!"));
		}

		@Test
		void failsToProvideArgumentsUsingFullyQualifiedNameSpecifyingInvalidParameterType() {
			String method = TestCase.class.getName() + "#stringStreamProviderWithParameter(example.FooBar)";
			var exception = assertThrows(JUnitException.class, () -> provideArguments(method).toArray());

			assertThat(exception).hasMessage("""
					Failed to load parameter type [example.FooBar] for method [stringStreamProviderWithParameter] \
					in class [org.junit.jupiter.params.provider.MethodArgumentsProviderTests$TestCase].""");
		}

		@Test
		void failsToProvideArgumentsUsingLocalQualifiedNameSpecifyingInvalidParameterType() {
			var method = "stringStreamProviderWithParameter(example.FooBar)";
			var exception = assertThrows(JUnitException.class,
				() -> provideArguments(this.testMethod, method).toArray());

			assertThat(exception).hasMessage("""
					Failed to load parameter type [example.FooBar] for method [stringStreamProviderWithParameter] \
					in class [org.junit.jupiter.params.provider.MethodArgumentsProviderTests$TestCase].""");
		}

		@Test
		void failsToProvideArgumentsUsingFullyQualifiedNameSpecifyingIncorrectParameterType() {
			String method = TestCase.class.getName() + "#stringStreamProviderWithParameter(java.lang.Integer)";
			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(method).toArray());

			assertThat(exception).hasMessage("""
					Could not find factory method [stringStreamProviderWithParameter(java.lang.Integer)] in \
					class [org.junit.jupiter.params.provider.MethodArgumentsProviderTests$TestCase]""");
		}

		@Test
		void failsToProvideArgumentsUsingLocalQualifiedNameSpecifyingIncorrectParameterType() {
			var method = "stringStreamProviderWithParameter(java.lang.Integer)";
			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(this.testMethod, method).toArray());

			assertThat(exception).hasMessage("""
					Could not find factory method [stringStreamProviderWithParameter(java.lang.Integer)] in \
					class [org.junit.jupiter.params.provider.MethodArgumentsProviderTests$TestCase]""");
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"org.junit.jupiter.params.provider.MethodArgumentsProviderTests$TestCase#stringStreamProviderWithArrayParameter(java.lang.String[])",
				"org.junit.jupiter.params.provider.MethodArgumentsProviderTests$TestCase#stringStreamProviderWithArrayParameter([Ljava.lang.String;)", })
		void providesArgumentsUsingFullyQualifiedNameSpecifyingObjectArrayParameter(String method) {
			var arguments = provideArguments(method);

			assertThat(arguments).containsExactly(array("foo :)"), array("bar :)"));
		}

		@ParameterizedTest
		@ValueSource(strings = { //
				"stringStreamProviderWithArrayParameter(java.lang.String[])",
				"stringStreamProviderWithArrayParameter([Ljava.lang.String;)" })
		void providesArgumentsUsingLocalQualifiedNameSpecifyingObjectArrayParameter(String method) {
			var arguments = provideArguments(this.testMethod, method);

			assertThat(arguments).containsExactly(array("foo :)"), array("bar :)"));
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"org.junit.jupiter.params.provider.MethodArgumentsProviderTests$TestCase#stringStreamProviderWithArrayParameter(int[])",
				"org.junit.jupiter.params.provider.MethodArgumentsProviderTests$TestCase#stringStreamProviderWithArrayParameter([I)", })
		void providesArgumentsUsingFullyQualifiedNameSpecifyingPrimitiveArrayParameter(String method) {
			var arguments = provideArguments(method);

			assertThat(arguments).containsExactly(array("foo 42"), array("bar 42"));
		}

		@ParameterizedTest
		@ValueSource(strings = { //
				"stringStreamProviderWithArrayParameter(int[])", //
				"stringStreamProviderWithArrayParameter([I)" })
		void providesArgumentsUsingLocalQualifiedNameSpecifyingPrimitiveArrayParameter(String method) {
			var arguments = provideArguments(this.testMethod, method);

			assertThat(arguments).containsExactly(array("foo 42"), array("bar 42"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "java.lang.String,java.lang.String", "java.lang.String, java.lang.String",
				"java.lang.String,    java.lang.String" })
		void providesArgumentsUsingFullyQualifiedNameSpecifyingMultipleParameters(String params) {
			var method = TestCase.class.getName() + "#stringStreamProviderWithOrWithoutParameter(" + params + ")";
			var arguments = provideArguments(method);

			assertThat(arguments).containsExactly(array("foo!!"), array("bar!!"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "java.lang.String,java.lang.String", "java.lang.String, java.lang.String",
				"java.lang.String,    java.lang.String" })
		void providesArgumentsUsingLocalQualifiedNameSpecifyingMultipleParameters(String params) {
			var arguments = provideArguments(this.testMethod,
				"stringStreamProviderWithOrWithoutParameter(" + params + ")");

			assertThat(arguments).containsExactly(array("foo!!"), array("bar!!"));
		}

		@Test
		void providesArgumentsUsingFullyQualifiedNameForOverloadedFactoryMethodWhenParameterListIsNotSpecified() {
			var arguments = provideArguments(TestCase.class.getName() + "#stringStreamProviderWithOrWithoutParameter");

			assertThat(arguments).containsExactly(array("foo"), array("bar"));
		}

		@Test
		void providesArgumentsUsingLocalQualifiedNameForOverloadedFactoryMethodWhenParameterListIsNotSpecified() {
			var arguments = provideArguments("stringStreamProviderWithOrWithoutParameter").toArray();

			assertThat(arguments).containsExactly(array("foo"), array("bar"));
		}

	}

	@Nested
	class ErrorCases {

		@Test
		void throwsExceptionWhenFullyQualifiedMethodNameSyntaxIsInvalid() {
			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments("org.example.wrongSyntax").toArray());

			assertThat(exception.getMessage()).isEqualTo(
				"[org.example.wrongSyntax] is not a valid fully qualified method name: "
						+ "it must start with a fully qualified class name followed by a '#' and then the method name, "
						+ "optionally followed by a parameter list enclosed in parentheses.");
		}

		@Test
		void throwsExceptionWhenClassForExternalFactoryMethodCannotBeLoaded() {
			var exception = assertThrows(JUnitException.class,
				() -> provideArguments("com.example.NonExistentClass#stringsProvider").toArray());

			assertThat(exception.getMessage()).isEqualTo("Could not load class [com.example.NonExistentClass]");
		}

		@Test
		void throwsExceptionWhenExternalFactoryMethodDoesNotExist() {
			String factoryClass = ExternalFactoryMethods.class.getName();

			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(factoryClass + "#nonExistentMethod").toArray());

			assertThat(exception.getMessage()).isEqualTo(
				"Could not find factory method [nonExistentMethod] in class [%s]", factoryClass);
		}

		@Test
		void throwsExceptionWhenLocalFactoryMethodDoesNotExist() {
			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments("nonExistentMethod").toArray());

			assertThat(exception.getMessage()).isEqualTo(
				"Could not find factory method [nonExistentMethod] in class [%s]", TestCase.class.getName());
		}

		@Test
		void throwsExceptionWhenExternalFactoryMethodAcceptingSingleArgumentDoesNotExist() {
			String factoryClass = ExternalFactoryMethods.class.getName();

			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(factoryClass + "#nonExistentMethod(int)").toArray());

			assertThat(exception.getMessage()).isEqualTo(
				"Could not find factory method [nonExistentMethod(int)] in class [%s]", factoryClass);
		}

		@Test
		void throwsExceptionWhenLocalFactoryMethodAcceptingSingleArgumentDoesNotExist() {
			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments("nonExistentMethod(int)").toArray());

			assertThat(exception.getMessage()).isEqualTo(
				"Could not find factory method [nonExistentMethod(int)] in class [%s]", TestCase.class.getName());
		}

		@Test
		void throwsExceptionWhenExternalFactoryMethodAcceptingMultipleArgumentsDoesNotExist() {
			String factoryClass = ExternalFactoryMethods.class.getName();

			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(factoryClass + "#nonExistentMethod(int, java.lang.String)").toArray());

			assertThat(exception.getMessage()).isEqualTo(
				"Could not find factory method [nonExistentMethod(int, java.lang.String)] in class [%s]", factoryClass);
		}

		@Test
		void throwsExceptionWhenLocalFactoryMethodAcceptingMultipleArgumentsDoesNotExist() {
			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments("nonExistentMethod(java.lang.String,int)").toArray());

			assertThat(exception.getMessage()).isEqualTo(
				"Could not find factory method [nonExistentMethod(java.lang.String,int)] in class [%s]",
				TestCase.class.getName());
		}

		@Test
		void throwsExceptionWhenExternalFactoryMethodHasInvalidReturnType() {
			String testClass = TestCase.class.getName();
			String factoryClass = ExternalFactoryMethods.class.getName();
			String factoryMethod = factoryClass + "#factoryWithInvalidReturnType";

			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(TestCase.class, false, factoryMethod).toArray());

			assertThat(exception.getMessage())//
					.containsSubsequence("Could not find valid factory method [" + factoryMethod + "] for test class [",
						testClass + "]", //
						"but found the following invalid candidate: ",
						"static java.lang.Object " + factoryClass + ".factoryWithInvalidReturnType()");
		}

		@Test
		void throwsExceptionWhenLocalFactoryMethodHasInvalidReturnType() {
			String testClass = TestCase.class.getName();
			String factoryClass = testClass;
			String factoryMethod = "factoryWithInvalidReturnType";

			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(factoryMethod).toArray());

			assertThat(exception.getMessage())//
					.containsSubsequence("Could not find valid factory method [" + factoryMethod + "] for test class [",
						factoryClass + "]", //
						"but found the following invalid candidate: ", //
						"static java.lang.Object " + factoryClass + ".factoryWithInvalidReturnType()");
		}

		@Test
		void throwsExceptionWhenMultipleDefaultFactoryMethodCandidatesExist() {
			var testClass = MultipleDefaultFactoriesTestCase.class;
			var methodName = "test";
			var testMethod = findMethod(testClass, methodName, String.class).get();

			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(testClass, testMethod, false, "").toArray());

			assertThat(exception.getMessage()).contains(//
				"2 factory methods named [test] were found in class [", testClass.getName() + "]: ", //
				"$MultipleDefaultFactoriesTestCase.test()", //
				"$MultipleDefaultFactoriesTestCase.test(int)"//
			);
		}

		@Test
		void throwsExceptionWhenMultipleInvalidDefaultFactoryMethodCandidatesExist() {
			var testClass = MultipleInvalidDefaultFactoriesTestCase.class;
			var methodName = "test";
			var testMethod = findMethod(testClass, methodName, String.class).get();

			var exception = assertThrows(PreconditionViolationException.class,
				() -> provideArguments(testClass, testMethod, false, "").toArray());

			assertThat(exception.getMessage()).contains(//
				"Could not find valid factory method [test] in class [", testClass.getName() + "]", //
				"but found the following invalid candidates: ", //
				"$MultipleInvalidDefaultFactoriesTestCase.test()", //
				"$MultipleInvalidDefaultFactoriesTestCase.test(int)"//
			);
		}

	}

	// -------------------------------------------------------------------------

	private static Object[] array(Object... objects) {
		return objects;
	}

	private Stream<Object[]> provideArguments(String... factoryMethodNames) {
		return provideArguments(TestCase.class, false, factoryMethodNames);
	}

	private Stream<Object[]> provideArguments(Method testMethod, String factoryMethodName) {
		return provideArguments(TestCase.class, testMethod, false, factoryMethodName);
	}

	private Stream<Object[]> provideArguments(Class<?> testClass, boolean allowNonStaticMethod,
			String... factoryMethodNames) {

		// Ensure we have a non-null test method, even if it's not a real test method.
		// If this throws an exception, make sure that the supplied test class defines a "void test()" method.
		Method testMethod = ReflectionUtils.findMethod(testClass, "test").get();
		return provideArguments(testClass, testMethod, allowNonStaticMethod, factoryMethodNames);
	}

	private Stream<Object[]> provideArguments(Class<?> testClass, Method testMethod, boolean allowNonStaticMethod,
			String... factoryMethodNames) {

		var methodSource = mock(MethodSource.class);

		when(methodSource.value()).thenReturn(factoryMethodNames);

		var extensionContext = mock(ExtensionContext.class);
		when(extensionContext.getTestClass()).thenReturn(Optional.of(testClass));
		when(extensionContext.getTestMethod()).thenReturn(Optional.of(testMethod));
		when(extensionContext.getExecutableInvoker()).thenReturn(
			new DefaultExecutableInvoker(extensionContext, extensionRegistry));

		doCallRealMethod().when(extensionContext).getRequiredTestMethod();
		doCallRealMethod().when(extensionContext).getRequiredTestClass();

		var testInstance = allowNonStaticMethod ? ReflectionUtils.newInstance(testClass) : null;
		when(extensionContext.getTestInstance()).thenReturn(Optional.ofNullable(testInstance));

		var lifeCycle = allowNonStaticMethod ? Lifecycle.PER_CLASS : Lifecycle.PER_METHOD;
		when(extensionContext.getTestInstanceLifecycle()).thenReturn(Optional.of(lifeCycle));

		var provider = new MethodArgumentsProvider();
		provider.accept(methodSource);
		return provider.provideArguments(extensionContext).map(Arguments::get);
	}

	// -------------------------------------------------------------------------

	static class DefaultFactoryMethodNameTestCase {

		// Test
		void testDefaultFactoryMethodName(String param) {
		}

		// Factory
		static Stream<String> testDefaultFactoryMethodName() {
			return Stream.of("foo", "bar");
		}
	}

	static class MultipleDefaultFactoriesTestCase {

		// Test
		void test(String param) {
		}

		// Factory
		static Stream<String> test() {
			return Stream.of();
		}

		// Another Factory
		static Stream<Integer> test(int num) {
			return Stream.of();
		}
	}

	static class MultipleInvalidDefaultFactoriesTestCase {

		// Test
		void test(String param) {
		}

		// NOT a Factory
		static String test() {
			return null;
		}

		// Also NOT a Factory
		static Object test(int num) {
			return null;
		}
	}

	static class TestCase {

		void test() {
		}

		// --- Invalid ---------------------------------------------------------

		static Object factoryWithInvalidReturnType() {
			return -1;
		}

		// --- Stream ----------------------------------------------------------

		static Stream<String> stringStreamProvider() {
			return Stream.of("foo", "bar");
		}

		static Stream<String> stringStreamProviderWithParameter(String parameter) {
			return Stream.of("foo" + parameter, "bar" + parameter);
		}

		static Stream<String> stringStreamProviderWithArrayParameter(String[] parameter) {
			String suffix = Arrays.stream(parameter).collect(Collectors.joining());
			return Stream.of("foo " + suffix, "bar " + suffix);
		}

		static Stream<String> stringStreamProviderWithArrayParameter(int[] parameter) {
			return stringStreamProviderWithArrayParameter(
				Arrays.stream(parameter).mapToObj(String::valueOf).toArray(String[]::new));
		}

		static Stream<String> stringStreamProviderWithOrWithoutParameter() {
			return stringStreamProvider();
		}

		static Stream<String> stringStreamProviderWithOrWithoutParameter(String parameter) {
			return stringStreamProviderWithParameter(parameter);
		}

		static Stream<String> stringStreamProviderWithOrWithoutParameter(String parameter1, String parameter2) {
			return stringStreamProviderWithParameter(parameter1 + parameter2);
		}

		// Overloaded method, but not a valid return type for a factory method
		static void stringStreamProviderWithOrWithoutParameter(String parameter1, int parameter2) {
		}

		// @ParameterizedTest
		// @MethodSource // use default, inferred factory method
		void overloadedStringStreamProvider(Object parameter) {
			// test implementation
		}

		// Default factory method for overloadedStringStreamProvider(Object)
		static Stream<String> overloadedStringStreamProvider(String parameter) {
			return stringStreamProviderWithParameter(parameter);
		}

		static DoubleStream doubleStreamProvider() {
			return DoubleStream.of(1.2, 3.4);
		}

		static LongStream longStreamProvider() {
			return LongStream.of(1L, 2L);
		}

		static IntStream intStreamProvider() {
			return IntStream.of(1, 2);
		}

		static Stream<int[]> intArrayStreamProvider() {
			return Stream.of(new int[] { 1, 2 }, new int[] { 3, 4 });
		}

		static Stream<int[][]> twoDimensionalIntArrayStreamProvider() {
			return Stream.of(new int[][] { { 1, 2 }, { 2, 3 } }, new int[][] { { 4, 5 }, { 5, 6 } });
		}

		static Stream<Object[]> objectArrayStreamProvider() {
			return Stream.of(new Object[] { "foo", 42 }, new Object[] { "bar", 23 });
		}

		static Stream<Object[][]> twoDimensionalObjectArrayStreamProvider() {
			return Stream.of(new Object[][] { { "a", 1 }, { "b", 2 } }, new Object[][] { { "c", 3 }, { "d", 4 } });
		}

		static Stream<Arguments> argumentsStreamProvider() {
			return objectArrayStreamProvider().map(Arguments::of);
		}

		// --- Iterable / Collection -------------------------------------------

		static Iterable<String> stringIterableProvider() {
			return TestCase::stringIteratorProvider;
		}

		static Iterable<Object[]> objectArrayIterableProvider() {
			return objectArrayListProvider();
		}

		static List<String> stringArrayListProvider() {
			return Arrays.asList("foo", "bar");
		}

		static List<Object[]> objectArrayListProvider() {
			return Arrays.asList(array("foo", 42), array("bar", 23));
		}

		// --- Iterator --------------------------------------------------------

		static Iterator<String> stringIteratorProvider() {
			return Arrays.asList("foo", "bar").iterator();
		}

		// --- Array of primitives ---------------------------------------------

		static boolean[] booleanArrayProvider() {
			return new boolean[] { true, false };
		}

		static byte[] byteArrayProvider() {
			return new byte[] { (byte) 1, Byte.MIN_VALUE };
		}

		static char[] charArrayProvider() {
			return new char[] { (char) 1, Character.MIN_VALUE };
		}

		static double[] doubleArrayProvider() {
			return new double[] { 1d, Double.MIN_VALUE };
		}

		static float[] floatArrayProvider() {
			return new float[] { 1f, Float.MIN_VALUE };
		}

		static int[] intArrayProvider() {
			return new int[] { 47, Integer.MIN_VALUE };
		}

		static long[] longArrayProvider() {
			return new long[] { 47L, Long.MIN_VALUE };
		}

		static short[] shortArrayProvider() {
			return new short[] { (short) 47, Short.MIN_VALUE };
		}

		// --- Array of objects ------------------------------------------------

		static Object[] objectArrayProvider() {
			return new Object[] { 42, "bar" };
		}

		static String[] stringArrayProvider() {
			return new String[] { "foo", "bar" };
		}

		static Object[][] twoDimensionalObjectArrayProvider() {
			return new Object[][] { { "foo", 42 }, { "bar", 23 } };
		}

	}

	// This test case mimics @TestInstance(Lifecycle.PER_CLASS)
	static class NonStaticTestCase {

		void test() {
		}

		Stream<String> nonStaticStringStreamProvider() {
			return Stream.of("foo", "bar");
		}
	}

	static class ExternalFactoryMethods {

		static Object factoryWithInvalidReturnType() {
			return -1;
		}

		static Stream<String> stringsProvider() {
			return Stream.of("string1", "string2");
		}

		static class Nested {

			static Stream<String> stringsProvider() {
				return Stream.of("nested string1", "nested string2");
			}
		}
	}

	static class StringResolver implements ParameterResolver {

		@Override
		public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return parameterContext.getParameter().getType() == String.class;
		}

		@Override
		public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return "!";
		}
	}

	static class StringArrayResolver implements ParameterResolver {

		@Override
		public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return parameterContext.getParameter().getType() == String[].class;
		}

		@Override
		public String[] resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return new String[] { ":", ")" };
		}
	}

	static class IntArrayResolver implements ParameterResolver {

		@Override
		public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return parameterContext.getParameter().getType() == int[].class;
		}

		@Override
		public int[] resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return new int[] { 4, 2 };
		}
	}

}
