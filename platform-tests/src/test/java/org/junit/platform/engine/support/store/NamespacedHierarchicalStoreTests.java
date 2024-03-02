/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.engine.support.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.platform.commons.test.ConcurrencyTestingUtils.executeConcurrently;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NamespacedHierarchicalStore}.
 *
 * @since 5.0
 */
public class NamespacedHierarchicalStoreTests {

	private final Object key = "key";
	private final Object value = "value";

	private final String namespace = "ns";

	private final NamespacedHierarchicalStore.CloseAction<String> closeAction = mock();
	private final NamespacedHierarchicalStore<String> grandParentStore = new NamespacedHierarchicalStore<>(null,
		closeAction);
	private final NamespacedHierarchicalStore<String> parentStore = grandParentStore.newChild();
	private final NamespacedHierarchicalStore<String> store = parentStore.newChild();

	@Nested
	class StoringValuesTests {

		@Test
		void getWithUnknownKeyReturnsNull() {
			assertNull(store.get(namespace, "unknown key"));
		}

		@Test
		void putAndGetWithSameKey() {
			store.put(namespace, key, value);
			assertEquals(value, store.get(namespace, key));
		}

		@Test
		void valueCanBeReplaced() {
			store.put(namespace, key, value);

			Object newValue = new Object();
			assertEquals(value, store.put(namespace, key, newValue));

			assertEquals(newValue, store.get(namespace, key));
		}

		@Test
		void valueIsComputedIfAbsent() {
			assertNull(store.get(namespace, key));
			assertEquals(value, store.getOrComputeIfAbsent(namespace, key, innerKey -> value));
			assertEquals(value, store.get(namespace, key));
		}

		@Test
		void valueIsNotComputedIfPresentLocally() {
			store.put(namespace, key, value);

			assertEquals(value, store.getOrComputeIfAbsent(namespace, key, innerKey -> "a different value"));
			assertEquals(value, store.get(namespace, key));
		}

		@Test
		void valueIsNotComputedIfPresentInParent() {
			parentStore.put(namespace, key, value);

			assertEquals(value, store.getOrComputeIfAbsent(namespace, key, k -> "a different value"));
			assertEquals(value, store.get(namespace, key));
		}

		@Test
		void valueIsNotComputedIfPresentInGrandParent() {
			grandParentStore.put(namespace, key, value);

			assertEquals(value, store.getOrComputeIfAbsent(namespace, key, k -> "a different value"));
			assertEquals(value, store.get(namespace, key));
		}

		@Test
		void nullIsAValidValueToPut() {
			store.put(namespace, key, null);

			assertNull(store.getOrComputeIfAbsent(namespace, key, innerKey -> "a different value"));
			assertNull(store.get(namespace, key));
		}

		@Test
		void keysCanBeRemoved() {
			store.put(namespace, key, value);
			assertEquals(value, store.remove(namespace, key));

			assertNull(store.get(namespace, key));
			assertEquals("a different value",
				store.getOrComputeIfAbsent(namespace, key, innerKey -> "a different value"));
		}

		@Test
		void sameKeyWithDifferentNamespaces() {
			Object value1 = createObject("value1");
			String namespace1 = "ns1";

			Object value2 = createObject("value2");
			String namespace2 = "ns2";

			store.put(namespace1, key, value1);
			store.put(namespace2, key, value2);

			assertEquals(value1, store.get(namespace1, key));
			assertEquals(value2, store.get(namespace2, key));
		}

		@Test
		void valueIsComputedIfAbsentInDifferentNamespace() {
			String namespace1 = "ns1";
			String namespace2 = "ns2";

			assertEquals(value, store.getOrComputeIfAbsent(namespace1, key, innerKey -> value));
			assertEquals(value, store.get(namespace1, key));

			assertNull(store.get(namespace2, key));
		}

		@Test
		void keyIsOnlyRemovedInGivenNamespace() {
			String namespace1 = "ns1";
			String namespace2 = "ns2";

			Object value1 = createObject("value1");
			Object value2 = createObject("value2");

			store.put(namespace1, key, value1);
			store.put(namespace2, key, value2);
			store.remove(namespace1, key);

			assertNull(store.get(namespace1, key));
			assertEquals(value2, store.get(namespace2, key));
		}

		@Test
		void getWithTypeSafetyAndInvalidRequiredTypeThrowsException() {
			Integer key = 42;
			String value = "enigma";
			store.put(namespace, key, value);

			Exception exception = assertThrows(NamespacedHierarchicalStoreException.class,
				() -> store.get(namespace, key, Number.class));
			assertEquals(
				"Object stored under key [42] is not of required type [java.lang.Number], but was [java.lang.String]: enigma",
				exception.getMessage());
		}

		@Test
		void getWithTypeSafety() {
			Integer key = 42;
			String value = "enigma";
			store.put(namespace, key, value);

			// The fact that we can declare this as a String suffices for testing the required type.
			String requiredTypeValue = store.get(namespace, key, String.class);
			assertEquals(value, requiredTypeValue);
		}

		@Test
		void getWithTypeSafetyAndPrimitiveValueType() {
			String key = "enigma";
			int value = 42;
			store.put(namespace, key, value);

			// The fact that we can declare this as an int/Integer suffices for testing the required type.
			int requiredInt = store.get(namespace, key, int.class);
			Integer requiredInteger = store.get(namespace, key, Integer.class);
			assertEquals(value, requiredInt);
			assertEquals(value, requiredInteger.intValue());
		}

		@Test
		void getNullValueWithTypeSafety() {
			store.put(namespace, key, null);

			// The fact that we can declare this as a String suffices for testing the required type.
			String requiredTypeValue = store.get(namespace, key, String.class);
			assertNull(requiredTypeValue);
		}

		@Test
		void getOrComputeIfAbsentWithTypeSafetyAndInvalidRequiredTypeThrowsException() {
			String key = "pi";
			Float value = 3.14f;

			// Store a Float...
			store.put(namespace, key, value);

			// But declare that our function creates a String...
			Function<String, String> defaultCreator = k -> "enigma";

			Exception exception = assertThrows(NamespacedHierarchicalStoreException.class,
				() -> store.getOrComputeIfAbsent(namespace, key, defaultCreator, String.class));
			assertEquals(
				"Object stored under key [pi] is not of required type [java.lang.String], but was [java.lang.Float]: 3.14",
				exception.getMessage());
		}

		@Test
		void getOrComputeIfAbsentWithTypeSafety() {
			Integer key = 42;
			String value = "enigma";

			// The fact that we can declare this as a String suffices for testing the required type.
			String computedValue = store.getOrComputeIfAbsent(namespace, key, k -> value, String.class);
			assertEquals(value, computedValue);
		}

		@Test
		void getOrComputeIfAbsentWithTypeSafetyAndPrimitiveValueType() {
			String key = "enigma";
			int value = 42;

			// The fact that we can declare this as an int/Integer suffices for testing the required type.
			int computedInt = store.getOrComputeIfAbsent(namespace, key, k -> value, int.class);
			Integer computedInteger = store.getOrComputeIfAbsent(namespace, key, k -> value, Integer.class);
			assertEquals(value, computedInt);
			assertEquals(value, computedInteger.intValue());
		}

		@Test
		void getOrComputeIfAbsentWithExceptionThrowingCreatorFunction() {
			var e = assertThrows(RuntimeException.class, () -> store.getOrComputeIfAbsent(namespace, key, __ -> {
				throw new RuntimeException("boom");
			}));
			assertSame(e, assertThrows(RuntimeException.class, () -> store.get(namespace, key)));
			assertSame(e, assertThrows(RuntimeException.class, () -> store.remove(namespace, key)));
		}

		@Test
		void removeWithTypeSafetyAndInvalidRequiredTypeThrowsException() {
			Integer key = 42;
			String value = "enigma";
			store.put(namespace, key, value);

			Exception exception = assertThrows(NamespacedHierarchicalStoreException.class,
				() -> store.remove(namespace, key, Number.class));
			assertEquals(
				"Object stored under key [42] is not of required type [java.lang.Number], but was [java.lang.String]: enigma",
				exception.getMessage());
		}

		@Test
		void removeWithTypeSafety() {
			Integer key = 42;
			String value = "enigma";
			store.put(namespace, key, value);

			// The fact that we can declare this as a String suffices for testing the required type.
			String removedValue = store.remove(namespace, key, String.class);
			assertEquals(value, removedValue);
			assertNull(store.get(namespace, key));
		}

		@Test
		void removeWithTypeSafetyAndPrimitiveValueType() {
			String key = "enigma";
			int value = 42;
			store.put(namespace, key, value);

			// The fact that we can declare this as an int suffices for testing the required type.
			int requiredInt = store.remove(namespace, key, int.class);
			assertEquals(value, requiredInt);

			store.put(namespace, key, value);
			// The fact that we can declare this as an Integer suffices for testing the required type.
			Integer requiredInteger = store.get(namespace, key, Integer.class);
			assertEquals(value, requiredInteger.intValue());
		}

		@Test
		void removeNullValueWithTypeSafety() {
			Integer key = 42;
			store.put(namespace, key, null);

			// The fact that we can declare this as a String suffices for testing the required type.
			String removedValue = store.remove(namespace, key, String.class);
			assertNull(removedValue);
			assertNull(store.get(namespace, key));
		}

		@Test
		void simulateRaceConditionInGetOrComputeIfAbsent() throws Exception {
			int threads = 10;
			AtomicInteger counter = new AtomicInteger();
			List<Object> values;

			try (var localStore = new NamespacedHierarchicalStore<>(null)) {
				values = executeConcurrently(threads, //
					() -> localStore.getOrComputeIfAbsent(namespace, key, it -> counter.incrementAndGet()));
			}

			assertEquals(1, counter.get());
			assertThat(values).hasSize(threads).containsOnly(1);
		}
	}

	@Nested
	class InheritedValuesTests {

		@Test
		void valueFromParentIsVisible() {
			parentStore.put(namespace, key, value);
			assertEquals(value, store.get(namespace, key));
		}

		@Test
		void valueFromParentCanBeOverriddenInChild() {
			parentStore.put(namespace, key, value);

			Object otherValue = new Object();
			store.put(namespace, key, otherValue);
			assertEquals(otherValue, store.get(namespace, key));

			assertEquals(value, parentStore.get(namespace, key));
		}
	}

	@Nested
	class CompositeNamespaceTests {

		@Test
		void additionNamespacePartMakesADifference() {

			String ns1 = "part1/part2";
			String ns2 = "part1";

			Object value2 = createObject("value2");

			parentStore.put(ns1, key, value);
			parentStore.put(ns2, key, value2);

			assertEquals(value, store.get(ns1, key));
			assertEquals(value2, store.get(ns2, key));
		}

	}

	@Nested
	class CloseActionTests {

		@Test
		void callsCloseActionInReverseInsertionOrderWhenClosingStore() throws Throwable {
			store.put(namespace, "key1", "value1");
			store.put(namespace, "key2", "value2");
			store.put(namespace, "key3", "value3");
			verifyNoInteractions(closeAction);

			store.close();
			var inOrder = inOrder(closeAction);
			inOrder.verify(closeAction).close(namespace, "key3", "value3");
			inOrder.verify(closeAction).close(namespace, "key2", "value2");
			inOrder.verify(closeAction).close(namespace, "key1", "value1");
		}

		@Test
		void doesNotCallCloseActionForRemovedValues() {
			store.put(namespace, key, value);
			store.remove(namespace, key);

			store.close();

			verifyNoInteractions(closeAction);
		}

		@Test
		void doesNotCallCloseActionForReplacedValues() throws Throwable {
			store.put(namespace, key, "value1");
			store.put(namespace, key, "value2");

			store.close();

			verify(closeAction).close(namespace, key, "value2");
			verifyNoMoreInteractions(closeAction);
		}

		@Test
		void doesNotCallCloseActionForNullValues() {
			store.put(namespace, key, null);

			store.close();

			verifyNoInteractions(closeAction);
		}

		@Test
		void ignoresStoredValuesThatThrewExceptionsDuringCleanup() {
			assertThrows(RuntimeException.class, () -> store.getOrComputeIfAbsent(namespace, key, __ -> {
				throw new RuntimeException("boom");
			}));

			assertDoesNotThrow(store::close);

			verifyNoInteractions(closeAction);
		}

		@Test
		void doesNotIgnoreStoredValuesThatThrewUnrecoverableFailuresDuringCleanup() {
			assertThrows(OutOfMemoryError.class, () -> store.getOrComputeIfAbsent(namespace, key, __ -> {
				throw new OutOfMemoryError();
			}));

			assertThrows(OutOfMemoryError.class, store::close);

			verifyNoInteractions(closeAction);
		}
	}

	private Object createObject(final String display) {
		return new Object() {

			@Override
			public String toString() {
				return display;
			}
		};
	}

}
