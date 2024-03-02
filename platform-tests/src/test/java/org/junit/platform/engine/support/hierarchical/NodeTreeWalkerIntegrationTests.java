/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.engine.support.hierarchical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.commons.util.CollectionUtils.getOnlyElement;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.support.hierarchical.ExclusiveResource.GLOBAL_READ;
import static org.junit.platform.engine.support.hierarchical.ExclusiveResource.GLOBAL_READ_WRITE;
import static org.junit.platform.engine.support.hierarchical.ExclusiveResource.LockMode.READ;
import static org.junit.platform.engine.support.hierarchical.ExclusiveResource.LockMode.READ_WRITE;
import static org.junit.platform.engine.support.hierarchical.Node.ExecutionMode.SAME_THREAD;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;

/**
 * @since 1.3
 */
class NodeTreeWalkerIntegrationTests {

	LockManager lockManager = new LockManager();
	NodeTreeWalker nodeTreeWalker = new NodeTreeWalker(lockManager);

	@Test
	void pullUpExclusiveChildResourcesToTestClass() {
		var engineDescriptor = discover(TestCaseWithResourceLock.class);

		var advisor = nodeTreeWalker.walk(engineDescriptor);

		var testClassDescriptor = getOnlyElement(engineDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ), getReadWriteLock("a"), getReadWriteLock("b")));
		assertThat(advisor.getForcedExecutionMode(testClassDescriptor)).isEmpty();

		var testMethodDescriptor = getOnlyElement(testClassDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testMethodDescriptor)).extracting(allLocks()).isEqualTo(List.of());
		assertThat(advisor.getForcedExecutionMode(testMethodDescriptor)).contains(SAME_THREAD);
	}

	@Test
	void setsForceExecutionModeForChildrenWithWriteLocksOnClass() {
		var engineDescriptor = discover(TestCaseWithResourceWriteLockOnClass.class);

		var advisor = nodeTreeWalker.walk(engineDescriptor);

		var testClassDescriptor = getOnlyElement(engineDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ), getReadWriteLock("a")));
		assertThat(advisor.getForcedExecutionMode(testClassDescriptor)).isEmpty();

		var testMethodDescriptor = getOnlyElement(testClassDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testMethodDescriptor)).extracting(allLocks()).isEqualTo(List.of());
		assertThat(advisor.getForcedExecutionMode(testMethodDescriptor)).contains(SAME_THREAD);
	}

	@Test
	void doesntSetForceExecutionModeForChildrenWithReadLocksOnClass() {
		var engineDescriptor = discover(TestCaseWithResourceReadLockOnClass.class);

		var advisor = nodeTreeWalker.walk(engineDescriptor);

		var testClassDescriptor = getOnlyElement(engineDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ), getReadLock("a")));
		assertThat(advisor.getForcedExecutionMode(testClassDescriptor)).isEmpty();

		var testMethodDescriptor = getOnlyElement(testClassDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testMethodDescriptor)).extracting(allLocks()).isEqualTo(List.of());
		assertThat(advisor.getForcedExecutionMode(testMethodDescriptor)).isEmpty();
	}

	@Test
	void setsForceExecutionModeForChildrenWithReadLocksOnClassAndWriteLockOnTest() {
		var engineDescriptor = discover(TestCaseWithResourceReadLockOnClassAndWriteClockOnTestCase.class);

		var advisor = nodeTreeWalker.walk(engineDescriptor);

		var testClassDescriptor = getOnlyElement(engineDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ), getReadWriteLock("a")));
		assertThat(advisor.getForcedExecutionMode(testClassDescriptor)).isEmpty();

		var testMethodDescriptor = getOnlyElement(testClassDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testMethodDescriptor)).extracting(allLocks()).isEqualTo(List.of());
		assertThat(advisor.getForcedExecutionMode(testMethodDescriptor)).contains(SAME_THREAD);
	}

	@Test
	void doesntSetForceExecutionModeForChildrenWithReadLocksOnClassAndReadLockOnTest() {
		var engineDescriptor = discover(TestCaseWithResourceReadLockOnClassAndReadClockOnTestCase.class);

		var advisor = nodeTreeWalker.walk(engineDescriptor);

		var testClassDescriptor = getOnlyElement(engineDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ), getReadLock("a"), getReadLock("b")));
		assertThat(advisor.getForcedExecutionMode(testClassDescriptor)).isEmpty();

		var testMethodDescriptor = getOnlyElement(testClassDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testMethodDescriptor)).extracting(allLocks()).isEqualTo(List.of());
		assertThat(advisor.getForcedExecutionMode(testMethodDescriptor)).isEmpty();
	}

	@Test
	void leavesResourceLockOnTestMethodWhenClassDoesNotUseResource() {
		var engineDescriptor = discover(TestCaseWithoutResourceLock.class);

		var advisor = nodeTreeWalker.walk(engineDescriptor);

		var testClassDescriptor = getOnlyElement(engineDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ)));
		assertThat(advisor.getForcedExecutionMode(testClassDescriptor)).isEmpty();

		assertThat(testClassDescriptor.getChildren()).hasSize(2);
		var children = testClassDescriptor.getChildren().iterator();
		var testMethodDescriptor = children.next();
		assertThat(advisor.getResourceLock(testMethodDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getReadWriteLock("a")));
		assertThat(advisor.getForcedExecutionMode(testMethodDescriptor)).isEmpty();

		var nestedTestClassDescriptor = children.next();
		assertThat(advisor.getResourceLock(nestedTestClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getReadWriteLock("b"), getReadWriteLock("c")));
		assertThat(advisor.getForcedExecutionMode(nestedTestClassDescriptor)).isEmpty();

		var nestedTestMethodDescriptor = getOnlyElement(nestedTestClassDescriptor.getChildren());
		assertThat(advisor.getResourceLock(nestedTestMethodDescriptor)).extracting(allLocks()).isEqualTo(List.of());
		assertThat(advisor.getForcedExecutionMode(nestedTestMethodDescriptor)).contains(SAME_THREAD);
	}

	@Test
	void coarsensGlobalLockToEngineDescriptorChild() {
		var engineDescriptor = discover(TestCaseWithGlobalLockRequiringChild.class);

		var advisor = nodeTreeWalker.walk(engineDescriptor);

		var testClassDescriptor = getOnlyElement(engineDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ_WRITE)));
		assertThat(advisor.getForcedExecutionMode(testClassDescriptor)).isEmpty();

		var nestedTestClassDescriptor = getOnlyElement(testClassDescriptor.getChildren());
		assertThat(advisor.getResourceLock(nestedTestClassDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ)));
		assertThat(advisor.getForcedExecutionMode(nestedTestClassDescriptor)).contains(SAME_THREAD);

		var testMethodDescriptor = getOnlyElement(nestedTestClassDescriptor.getChildren());
		assertThat(advisor.getResourceLock(testMethodDescriptor)).extracting(allLocks()) //
				.isEqualTo(List.of(getLock(GLOBAL_READ_WRITE)));
		assertThat(advisor.getForcedExecutionMode(testMethodDescriptor)).contains(SAME_THREAD);
	}

	private static Function<org.junit.platform.engine.support.hierarchical.ResourceLock, List<Lock>> allLocks() {
		return ResourceLockSupport::getLocks;
	}

	private Lock getReadWriteLock(String key) {
		return getLock(new ExclusiveResource(key, READ_WRITE));
	}

	private Lock getReadLock(String key) {
		return getLock(new ExclusiveResource(key, READ));
	}

	private Lock getLock(ExclusiveResource exclusiveResource) {
		return getOnlyElement(ResourceLockSupport.getLocks(lockManager.getLockForResource(exclusiveResource)));
	}

	private TestDescriptor discover(Class<?> testClass) {
		var discoveryRequest = request().selectors(selectClass(testClass)).build();
		return new JupiterTestEngine().discover(discoveryRequest, UniqueId.forEngine("junit-jupiter"));
	}

	@ResourceLock("a")
	static class TestCaseWithResourceLock {
		@Test
		@ResourceLock("b")
		void test() {
		}
	}

	static class TestCaseWithoutResourceLock {
		@Test
		@ResourceLock("a")
		void test() {
		}

		@Nested
		@ResourceLock("c")
		class NestedTestCaseWithResourceLock {
			@Test
			@ResourceLock("b")
			void test() {
			}
		}
	}

	static class TestCaseWithGlobalLockRequiringChild {
		@Nested
		class NestedTestCaseWithResourceLock {
			@Test
			@ResourceLock(ExclusiveResource.GLOBAL_KEY)
			void test() {
			}
		}
	}

	@ResourceLock("a")
	static class TestCaseWithResourceWriteLockOnClass {
		@Test
		void test() {
		}
	}

	@ResourceLock(value = "a", mode = ResourceAccessMode.READ)
	static class TestCaseWithResourceReadLockOnClass {
		@Test
		void test() {
		}
	}

	@ResourceLock(value = "a", mode = ResourceAccessMode.READ)
	static class TestCaseWithResourceReadLockOnClassAndWriteClockOnTestCase {
		@Test
		@ResourceLock("a")
		void test() {
		}
	}

	@ResourceLock(value = "a", mode = ResourceAccessMode.READ)
	static class TestCaseWithResourceReadLockOnClassAndReadClockOnTestCase {
		@Test
		@ResourceLock(value = "b", mode = ResourceAccessMode.READ)
		void test() {
		}
	}
}
