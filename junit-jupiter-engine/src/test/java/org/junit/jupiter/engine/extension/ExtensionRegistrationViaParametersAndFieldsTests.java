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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotatedFields;
import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.jupiter.api.fixtures.TrackLogRecords;
import org.junit.jupiter.engine.AbstractJupiterTestEngineTests;
import org.junit.jupiter.engine.execution.injection.sample.LongParameterResolver;
import org.junit.platform.commons.PreconditionViolationException;
import org.junit.platform.commons.logging.LogRecordListener;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;

/**
 * Integration tests that verify support for extension registration via
 * {@link ExtendWith @ExtendWith} on parameters and fields.
 *
 * @since 5.8
 */
class ExtensionRegistrationViaParametersAndFieldsTests extends AbstractJupiterTestEngineTests {

	@Test
	void constructorParameter() {
		assertOneTestSucceeded(ConstructorParameterTestCase.class);
	}

	@Test
	void constructorParameterForNestedTestClass() {
		assertTestsSucceeded(NestedConstructorParameterTestCase.class, 2);
	}

	@Test
	void beforeAllMethodParameter() {
		assertOneTestSucceeded(BeforeAllParameterTestCase.class);
	}

	@Test
	void afterAllMethodParameter() {
		assertOneTestSucceeded(AfterAllParameterTestCase.class);
	}

	@Test
	void beforeEachMethodParameter() {
		assertOneTestSucceeded(BeforeEachParameterTestCase.class);
	}

	@Test
	void afterEachMethodParameter() {
		assertOneTestSucceeded(AfterEachParameterTestCase.class);
	}

	@Test
	void testMethodParameter() {
		assertOneTestSucceeded(TestMethodParameterTestCase.class);
	}

	@Test
	void testFactoryMethodParameter() {
		assertTestsSucceeded(TestFactoryMethodParameterTestCase.class, 2);
	}

	@Test
	void testTemplateMethodParameter() {
		assertTestsSucceeded(TestTemplateMethodParameterTestCase.class, 2);
	}

	@Test
	void staticField() {
		assertOneTestSucceeded(StaticFieldTestCase.class);
	}

	@Test
	void instanceField() {
		assertOneTestSucceeded(InstanceFieldTestCase.class);
	}

	@Test
	void fieldsWithTestInstancePerClass() {
		assertOneTestSucceeded(TestInstancePerClassFieldTestCase.class);
	}

	@Test
	void multipleRegistrationsViaField(@TrackLogRecords LogRecordListener listener) {
		assertOneTestSucceeded(MultipleRegistrationsViaFieldTestCase.class);
		assertThat(getRegisteredLocalExtensions(listener)).containsExactly("LongParameterResolver", "DummyExtension");
	}

	@Test
	void duplicateRegistrationViaField() {
		Class<?> testClass = DuplicateRegistrationViaFieldTestCase.class;
		String expectedMessage = "Failed to register extension via field "
				+ "[org.junit.jupiter.api.extension.Extension "
				+ "org.junit.jupiter.engine.extension.ExtensionRegistrationViaParametersAndFieldsTests$DuplicateRegistrationViaFieldTestCase.dummy]. "
				+ "The field registers an extension of type [org.junit.jupiter.engine.extension.DummyExtension] "
				+ "via @RegisterExtension and @ExtendWith, but only one registration of a given extension type is permitted.";

		executeTestsForClass(testClass).testEvents().assertThatEvents().haveExactly(1,
			finishedWithFailure(instanceOf(PreconditionViolationException.class), message(expectedMessage)));
	}

	@Test
	void registrationOrder(@TrackLogRecords LogRecordListener listener) {
		assertOneTestSucceeded(AllInOneWithTestInstancePerMethodTestCase.class);
		assertThat(getRegisteredLocalExtensions(listener))//
				.containsExactly(//
					"ClassLevelExtension2", // @RegisterExtension on static field
					"StaticField2", // @ExtendWith on static field
					"ClassLevelExtension1", // @RegisterExtension on static field
					"StaticField1", // @ExtendWith on static field
					"ConstructorParameter", // @ExtendWith on parameter in constructor
					"BeforeAllParameter", // @ExtendWith on parameter in static @BeforeAll method
					"BeforeEachParameter", // @ExtendWith on parameter in @BeforeEach method
					"AfterEachParameter", // @ExtendWith on parameter in @AfterEach method
					"AfterAllParameter", // @ExtendWith on parameter in static @AfterAll method
					"TestParameter", // @ExtendWith on parameter in @Test method
					"InstanceLevelExtension1", // @RegisterExtension on instance field
					"InstanceField1", // @ExtendWith on instance field
					"InstanceLevelExtension2", // @RegisterExtension on instance field
					"InstanceField2" // @ExtendWith on instance field
				);

		listener.clear();
		assertOneTestSucceeded(AllInOneWithTestInstancePerClassTestCase.class);
		assertThat(getRegisteredLocalExtensions(listener))//
				.containsExactly(//
					"ClassLevelExtension2", // @RegisterExtension on static field
					"StaticField2", // @ExtendWith on static field
					"ClassLevelExtension1", // @RegisterExtension on static field
					"StaticField1", // @ExtendWith on static field
					"ConstructorParameter", // @ExtendWith on parameter in constructor
					"BeforeAllParameter", // @ExtendWith on parameter in static @BeforeAll method
					"BeforeEachParameter", // @ExtendWith on parameter in @BeforeEach method
					"AfterEachParameter", // @ExtendWith on parameter in @AfterEach method
					"AfterAllParameter", // @ExtendWith on parameter in static @AfterAll method
					"InstanceLevelExtension1", // @RegisterExtension on instance field
					"InstanceField1", // @ExtendWith on instance field
					"InstanceLevelExtension2", // @RegisterExtension on instance field
					"InstanceField2", // @ExtendWith on instance field
					"TestParameter" // @ExtendWith on parameter in @Test method
				);
	}

	private List<String> getRegisteredLocalExtensions(LogRecordListener listener) {
		// @formatter:off
		return listener.stream(MutableExtensionRegistry.class, Level.FINER)
			.map(LogRecord::getMessage)
			.filter(message -> message.contains("local extension"))
			.map(message -> {
				message = message.replaceAll("from source .+", "");
				int indexOfDollarSign = message.indexOf("$");
				int indexOfAtSign = message.indexOf("@");
				int endIndex = (indexOfDollarSign > 1 ? indexOfDollarSign : indexOfAtSign);
				return message.substring(message.lastIndexOf('.') + 1, endIndex);
			})
			.collect(toList());
		// @formatter:on
	}

	private void assertOneTestSucceeded(Class<?> testClass) {
		assertTestsSucceeded(testClass, 1);
	}

	private void assertTestsSucceeded(Class<?> testClass, int expected) {
		executeTestsForClass(testClass).testEvents().assertStatistics(
			stats -> stats.started(expected).succeeded(expected).skipped(0).aborted(0).failed(0));
	}

	// -------------------------------------------------------------------

	/**
	 * The {@link MagicParameter.Extension} is first registered for the constructor
	 * and then used for lifecycle and test methods.
	 */
	@ExtendWith(LongParameterResolver.class)
	static class ConstructorParameterTestCase {

		ConstructorParameterTestCase(@MagicParameter("constructor") String text) {
			assertThat(text).isEqualTo("ConstructorParameterTestCase-0-constructor");
		}

		@BeforeEach
		void beforeEach(String text, TestInfo testInfo) {
			assertThat(text).isEqualTo("beforeEach-0-enigma");
			assertThat(testInfo).isNotNull();
		}

		@Test
		void test(TestInfo testInfo, String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("test-1-enigma");
		}

		/**
		 * Redeclaring {@code @MagicParameter} should not result in a
		 * {@link ParameterResolutionException}.
		 */
		@AfterEach
		void afterEach(Long number, TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-method");
		}

	}

	/**
	 * The {@link MagicParameter.Extension} is first registered for the constructor
	 * and then used for lifecycle and test methods.
	 */
	@Nested
	@ExtendWith(LongParameterResolver.class)
	class NestedConstructorParameterTestCase {

		NestedConstructorParameterTestCase(TestInfo testInfo, @MagicParameter("constructor") String text) {
			assertThat(testInfo).isNotNull();
			// Index is 2 instead of 1, since constructors for non-static nested classes
			// receive a reference to the enclosing instance as the first argument: this$0
			assertThat(text).isEqualTo("NestedConstructorParameterTestCase-2-constructor");
		}

		@BeforeEach
		void beforeEach(String text, TestInfo testInfo) {
			assertThat(text).isEqualTo("beforeEach-0-enigma");
			assertThat(testInfo).isNotNull();
		}

		@Test
		void test(TestInfo testInfo, String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("test-1-enigma");
		}

		/**
		 * Redeclaring {@code @MagicParameter} should not result in a
		 * {@link ParameterResolutionException}.
		 */
		@AfterEach
		void afterEach(Long number, TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-method");
		}

		@Nested
		class DoublyNestedConstructorParameterTestCase {

			DoublyNestedConstructorParameterTestCase(TestInfo testInfo, String text) {
				assertThat(testInfo).isNotNull();
				// Index is 2 instead of 1, since constructors for non-static nested classes
				// receive a reference to the enclosing instance as the first argument: this$0
				assertThat(text).isEqualTo("DoublyNestedConstructorParameterTestCase-2-enigma");
			}

			@BeforeEach
			void beforeEach(String text, TestInfo testInfo) {
				assertThat(text).isEqualTo("beforeEach-0-enigma");
				assertThat(testInfo).isNotNull();
			}

			@Test
			void test(TestInfo testInfo, String text) {
				assertThat(testInfo).isNotNull();
				assertThat(text).isEqualTo("test-1-enigma");
			}

			/**
			 * Redeclaring {@code @MagicParameter} should not result in a
			 * {@link ParameterResolutionException}.
			 */
			@AfterEach
			void afterEach(Long number, TestInfo testInfo, @MagicParameter("method") String text) {
				assertThat(number).isEqualTo(42L);
				assertThat(testInfo).isNotNull();
				assertThat(text).isEqualTo("afterEach-2-method");
			}
		}

	}

	/**
	 * The {@link MagicParameter.Extension} is first registered for the {@code @BeforeAll}
	 * method and then used for other lifecycle methods and test methods.
	 */
	@ExtendWith(LongParameterResolver.class)
	static class BeforeAllParameterTestCase {

		@BeforeAll
		static void beforeAll(@MagicParameter("method") String text, TestInfo testInfo) {
			assertThat(text).isEqualTo("beforeAll-0-method");
			assertThat(testInfo).isNotNull();
		}

		@BeforeEach
		void beforeEach(String text, TestInfo testInfo) {
			assertThat(text).isEqualTo("beforeEach-0-enigma");
			assertThat(testInfo).isNotNull();
		}

		@Test
		void test(TestInfo testInfo, String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("test-1-enigma");
		}

		/**
		 * Redeclaring {@code @MagicParameter} should not result in a
		 * {@link ParameterResolutionException}.
		 */
		@AfterEach
		void afterEach(Long number, TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-method");
		}

		@AfterAll
		static void afterAll(String text, TestInfo testInfo) {
			assertThat(text).isEqualTo("afterAll-0-enigma");
			assertThat(testInfo).isNotNull();
		}

	}

	/**
	 * The {@link MagicParameter.Extension} is first registered for the {@code @AfterAll}
	 * method, but that registration occurs before the test method is invoked, which
	 * allows the string parameters in the after-each and test methods to be resolved
	 * by the {@link MagicParameter.Extension} as well.
	 */
	@ExtendWith(LongParameterResolver.class)
	static class AfterAllParameterTestCase {

		@Test
		void test(TestInfo testInfo, String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("test-1-enigma");
		}

		@AfterEach
		void afterEach(Long number, TestInfo testInfo, String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-enigma");
		}

		@AfterAll
		static void afterAll(Long number, TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterAll-2-method");
		}

	}

	/**
	 * The {@link MagicParameter.Extension} is first registered for the {@code @BeforeEach}
	 * method and then used for other lifecycle methods and test methods.
	 */
	@ExtendWith(LongParameterResolver.class)
	static class BeforeEachParameterTestCase {

		@BeforeEach
		void beforeEach(@MagicParameter("method") String text, TestInfo testInfo) {
			assertThat(text).isEqualTo("beforeEach-0-method");
			assertThat(testInfo).isNotNull();
		}

		@Test
		void test(TestInfo testInfo, String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("test-1-enigma");
		}

		/**
		 * Redeclaring {@code @MagicParameter} should not result in a
		 * {@link ParameterResolutionException}.
		 */
		@AfterEach
		void afterEach(Long number, TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-method");
		}

	}

	/**
	 * The {@link MagicParameter.Extension} is first registered for the {@code @AfterEach}
	 * method, but that registration occurs before the test method is invoked, which
	 * allows the test method's parameter to be resolved by the {@link MagicParameter.Extension}
	 * as well.
	 */
	@ExtendWith(LongParameterResolver.class)
	static class AfterEachParameterTestCase {

		@Test
		void test(TestInfo testInfo, String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("test-1-enigma");
		}

		@AfterEach
		void afterEach(Long number, TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-method");
		}

	}

	/**
	 * The {@link MagicParameter.Extension} is first registered for the {@code @Test}
	 * method and then used for after-each lifecycle methods.
	 */
	@ExtendWith(LongParameterResolver.class)
	static class TestMethodParameterTestCase {

		@Test
		void test(TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("test-1-method");
		}

		@AfterEach
		void afterEach(Long number, TestInfo testInfo, String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-enigma");
		}

	}

	/**
	 * The {@link MagicParameter.Extension} is first registered for the {@code @TestFactory}
	 * method and then used for after-each lifecycle methods.
	 */
	@ExtendWith(LongParameterResolver.class)
	static class TestFactoryMethodParameterTestCase {

		@TestFactory
		Stream<DynamicTest> testFactory(TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("testFactory-1-method");

			return IntStream.of(2, 4).mapToObj(num -> dynamicTest("" + num, () -> assertEquals(0, num % 2)));
		}

		@AfterEach
		void afterEach(Long number, TestInfo testInfo, String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-enigma");
		}

	}

	/**
	 * The {@link MagicParameter.Extension} is first registered for the {@code @TestTemplate}
	 * method and then used for after-each lifecycle methods.
	 */
	@ExtendWith(LongParameterResolver.class)
	static class TestTemplateMethodParameterTestCase {

		@TestTemplate
		@ExtendWith(TwoInvocationsContextProvider.class)
		void testTemplate(TestInfo testInfo, @MagicParameter("method") String text) {
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("testTemplate-1-method");
		}

		@AfterEach
		void afterEach(Long number, TestInfo testInfo, String text) {
			assertThat(number).isEqualTo(42L);
			assertThat(testInfo).isNotNull();
			assertThat(text).isEqualTo("afterEach-2-enigma");
		}

	}

	private static class TwoInvocationsContextProvider implements TestTemplateInvocationContextProvider {

		@Override
		public boolean supportsTestTemplate(ExtensionContext context) {
			return true;
		}

		@Override
		public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
			return Stream.of(emptyTestTemplateInvocationContext(), emptyTestTemplateInvocationContext());
		}

		private static TestTemplateInvocationContext emptyTestTemplateInvocationContext() {
			return new TestTemplateInvocationContext() {
			};
		}
	}

	static class MultipleRegistrationsViaFieldTestCase {

		@ExtendWith(LongParameterResolver.class)
		@RegisterExtension
		Extension dummy = new DummyExtension();

		@Test
		void test() {
		}
	}

	static class DuplicateRegistrationViaFieldTestCase {

		@ExtendWith(DummyExtension.class)
		@RegisterExtension
		Extension dummy = new DummyExtension();

		@Test
		void test() {
		}
	}

	/**
	 * The {@link MagicField.Extension} is registered via a static field.
	 */
	static class StaticFieldTestCase {

		@MagicField
		private static String staticField1;

		@MagicField
		static String staticField2;

		@BeforeAll
		static void beforeAll() {
			assertThat(staticField1).isEqualTo("beforeAll - staticField1");
			assertThat(staticField2).isEqualTo("beforeAll - staticField2");
		}

		@Test
		void test() {
			assertThat(staticField1).isEqualTo("beforeAll - staticField1");
			assertThat(staticField2).isEqualTo("beforeAll - staticField2");
		}
	}

	/**
	 * The {@link MagicField.Extension} is registered via an instance field.
	 */
	static class InstanceFieldTestCase {

		@MagicField
		String instanceField1;

		@MagicField
		private String instanceField2;

		@Test
		void test() {
			assertThat(instanceField1).isEqualTo("beforeEach - instanceField1");
			assertThat(instanceField2).isEqualTo("beforeEach - instanceField2");
		}
	}

	/**
	 * The {@link MagicField.Extension} is registered via a static field and
	 * an instance field.
	 */
	@TestInstance(Lifecycle.PER_CLASS)
	static class TestInstancePerClassFieldTestCase {

		@MagicField
		static String staticField;

		@MagicField
		String instanceField;

		@BeforeAll
		void beforeAll() {
			assertThat(staticField).isEqualTo("beforeAll - staticField");
			assertThat(instanceField).isNull();
		}

		@Test
		void test() {
			assertThat(staticField).isEqualTo("beforeAll - staticField");
			assertThat(instanceField).isEqualTo("beforeEach - instanceField");
		}
	}

	@TestInstance(Lifecycle.PER_METHOD)
	static class AllInOneWithTestInstancePerMethodTestCase {

		@StaticField1
		@Order(Integer.MAX_VALUE)
		static String staticField1;

		@StaticField2
		@ExtendWith(StaticField2.Extension.class)
		@Order(3)
		static String staticField2;

		@RegisterExtension
		private static Extension classLevelExtension1 = new ClassLevelExtension1();

		@RegisterExtension
		@Order(1)
		static Extension classLevelExtension2 = new ClassLevelExtension2();

		@InstanceField1
		@Order(2)
		String instanceField1;

		@InstanceField2
		@ExtendWith(InstanceField2.Extension.class)
		String instanceField2;

		@RegisterExtension
		@Order(1)
		private Extension instanceLevelExtension1 = new InstanceLevelExtension1();

		@RegisterExtension
		@Order(3)
		Extension instanceLevelExtension2 = new InstanceLevelExtension2();

		AllInOneWithTestInstancePerMethodTestCase(@ConstructorParameter String text) {
			assertThat(text).isEqualTo("enigma");
		}

		@BeforeAll
		static void beforeAll(@ExtendWith(BeforeAllParameter.Extension.class) @BeforeAllParameter String text) {
			assertThat(text).isEqualTo("enigma");
			assertThat(staticField1).isEqualTo("beforeAll - staticField1");
			assertThat(staticField2).isEqualTo("beforeAll - staticField2");
		}

		@BeforeEach
		void beforeEach(@BeforeEachParameter String text) {
			assertThat(text).isEqualTo("enigma");
			assertThat(staticField1).isEqualTo("beforeAll - staticField1");
			assertThat(staticField2).isEqualTo("beforeAll - staticField2");
			assertThat(instanceField1).isEqualTo("beforeEach - instanceField1");
			assertThat(instanceField2).isEqualTo("beforeEach - instanceField2");
		}

		@Test
		void test(@TestParameter String text) {
			assertThat(text).isEqualTo("enigma");
			assertThat(staticField1).isEqualTo("beforeAll - staticField1");
			assertThat(staticField2).isEqualTo("beforeAll - staticField2");
			assertThat(instanceField1).isEqualTo("beforeEach - instanceField1");
			assertThat(instanceField2).isEqualTo("beforeEach - instanceField2");
		}

		@AfterEach
		void afterEach(@AfterEachParameter String text) {
			assertThat(text).isEqualTo("enigma");
			assertThat(staticField1).isEqualTo("beforeAll - staticField1");
			assertThat(staticField2).isEqualTo("beforeAll - staticField2");
			assertThat(instanceField1).isEqualTo("beforeEach - instanceField1");
			assertThat(instanceField2).isEqualTo("beforeEach - instanceField2");
		}

		@AfterAll
		static void afterAll(@AfterAllParameter String text) {
			assertThat(text).isEqualTo("enigma");
			assertThat(staticField1).isEqualTo("beforeAll - staticField1");
			assertThat(staticField2).isEqualTo("beforeAll - staticField2");
		}

	}

	@TestInstance(Lifecycle.PER_CLASS)
	static class AllInOneWithTestInstancePerClassTestCase extends AllInOneWithTestInstancePerMethodTestCase {

		AllInOneWithTestInstancePerClassTestCase(@ConstructorParameter String text) {
			super(text);
		}
	}

}

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MagicParameter.Extension.class)
@interface MagicParameter {

	String value();

	class Extension implements ParameterResolver {

		@Override
		public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			return parameterContext.getParameter().getType() == String.class;
		}

		@Override
		public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
			String text = parameterContext.findAnnotation(MagicParameter.class)//
					.map(MagicParameter::value)//
					.orElse("enigma");
			Executable declaringExecutable = parameterContext.getDeclaringExecutable();
			String name = declaringExecutable instanceof Constructor
					? declaringExecutable.getDeclaringClass().getSimpleName()
					: declaringExecutable.getName();
			return String.format("%s-%d-%s", name, parameterContext.getIndex(), text);
		}
	}
}

@SuppressWarnings("unused")
class BaseParameterExtension<T extends Annotation> implements ParameterResolver {

	private final Class<T> annotationType;

	@SuppressWarnings("unchecked")
	BaseParameterExtension() {
		Type genericSuperclass = getClass().getGenericSuperclass();
		this.annotationType = (Class<T>) ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
	}

	@Override
	public final boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		return parameterContext.isAnnotated(this.annotationType)
				&& parameterContext.getParameter().getType() == String.class;
	}

	@Override
	public final Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		return "enigma";
	}
}

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ConstructorParameter.Extension.class)
@interface ConstructorParameter {
	class Extension extends BaseParameterExtension<ConstructorParameter> {
	}
}

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
// Intentionally NOT annotated as follows
// @ExtendWith(BeforeAllParameter.Extension.class)
@interface BeforeAllParameter {
	class Extension extends BaseParameterExtension<BeforeAllParameter> {
	}
}

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(AfterAllParameter.Extension.class)
@interface AfterAllParameter {
	class Extension extends BaseParameterExtension<AfterAllParameter> {
	}
}

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(BeforeEachParameter.Extension.class)
@interface BeforeEachParameter {
	class Extension extends BaseParameterExtension<BeforeEachParameter> {
	}
}

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(AfterEachParameter.Extension.class)
@interface AfterEachParameter {
	class Extension extends BaseParameterExtension<AfterEachParameter> {
	}
}

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestParameter.Extension.class)
@interface TestParameter {
	class Extension extends BaseParameterExtension<TestParameter> {
	}
}

class DummyExtension implements Extension {
}

class BaseFieldExtension<T extends Annotation> implements BeforeAllCallback, BeforeEachCallback {

	private final Class<T> annotationType;

	@SuppressWarnings("unchecked")
	BaseFieldExtension() {
		Type genericSuperclass = getClass().getGenericSuperclass();
		this.annotationType = (Class<T>) ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
	}

	@Override
	public final void beforeAll(ExtensionContext context) throws Exception {
		injectFields("beforeAll", context.getRequiredTestClass(), null, ReflectionUtils::isStatic);
	}

	@Override
	public final void beforeEach(ExtensionContext context) throws Exception {
		injectFields("beforeEach", context.getRequiredTestClass(), context.getRequiredTestInstance(),
			ReflectionUtils::isNotStatic);
	}

	private void injectFields(String trigger, Class<?> testClass, Object instance, Predicate<Field> predicate) {
		findAnnotatedFields(testClass, this.annotationType, predicate).forEach(field -> {
			try {
				makeAccessible(field).set(instance, trigger + " - " + field.getName());
			}
			catch (Throwable t) {
				throw ExceptionUtils.throwAsUncheckedException(t);
			}
		});
	}
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MagicField.Extension.class)
@interface MagicField {
	class Extension extends BaseFieldExtension<MagicField> {
	}
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(InstanceField1.Extension.class)
@interface InstanceField1 {
	class Extension extends BaseFieldExtension<InstanceField1> {
	}
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
// Intentionally NOT annotated as follows
// @ExtendWith(InstanceField2.Extension.class)
@interface InstanceField2 {
	class Extension extends BaseFieldExtension<InstanceField2> {
	}
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(StaticField1.Extension.class)
@interface StaticField1 {
	class Extension extends BaseFieldExtension<StaticField1> {
	}
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
// Intentionally NOT annotated as follows
// @ExtendWith(StaticField2.Extension.class)
@interface StaticField2 {
	class Extension extends BaseFieldExtension<StaticField2> {
	}
}

class ClassLevelExtension1 implements Extension {
}

class ClassLevelExtension2 implements Extension {
}

class InstanceLevelExtension1 implements Extension {
}

class InstanceLevelExtension2 implements Extension {
}
