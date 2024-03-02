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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.fakes.TestDescriptorStub;

class TestPlanTests {

	private final ConfigurationParameters configParams = mock();

	private final EngineDescriptor engineDescriptor = new EngineDescriptor(UniqueId.forEngine("foo"), "Foo");

	@Test
	void doesNotContainTestsForEmptyContainers() {
		engineDescriptor.addChild(
			new AbstractTestDescriptor(engineDescriptor.getUniqueId().append("test", "bar"), "Bar") {
				@Override
				public Type getType() {
					return Type.CONTAINER;
				}
			});

		var testPlan = TestPlan.from(Set.of(engineDescriptor), configParams);

		assertThat(testPlan.containsTests()).as("contains tests").isFalse();
	}

	@Test
	void containsTestsForTests() {
		engineDescriptor.addChild(
			new AbstractTestDescriptor(engineDescriptor.getUniqueId().append("test", "bar"), "Bar") {
				@Override
				public Type getType() {
					return Type.TEST;
				}
			});

		var testPlan = TestPlan.from(Set.of(engineDescriptor), configParams);

		assertThat(testPlan.containsTests()).as("contains tests").isTrue();
	}

	@Test
	void containsTestsForContainersThatMayRegisterTests() {
		engineDescriptor.addChild(
			new AbstractTestDescriptor(engineDescriptor.getUniqueId().append("test", "bar"), "Bar") {
				@Override
				public Type getType() {
					return Type.CONTAINER;
				}

				@Override
				public boolean mayRegisterTests() {
					return true;
				}
			});

		var testPlan = TestPlan.from(Set.of(engineDescriptor), configParams);

		assertThat(testPlan.containsTests()).as("contains tests").isTrue();
	}

	@Test
	void acceptsVisitorsInDepthFirstOrder() {
		var container = new TestDescriptorStub(engineDescriptor.getUniqueId().append("container", "bar"), "Bar");
		var test1 = new TestDescriptorStub(container.getUniqueId().append("test", "bar"), "Bar");
		container.addChild(test1);
		engineDescriptor.addChild(container);

		var engineDescriptor2 = new EngineDescriptor(UniqueId.forEngine("baz"), "Baz");
		var test2 = new TestDescriptorStub(engineDescriptor2.getUniqueId().append("test", "baz1"), "Baz");
		var test3 = new TestDescriptorStub(engineDescriptor2.getUniqueId().append("test", "baz2"), "Baz");
		engineDescriptor2.addChild(test2);
		engineDescriptor2.addChild(test3);

		var testPlan = TestPlan.from(List.of(engineDescriptor, engineDescriptor2), configParams);
		var visitor = mock(TestPlan.Visitor.class);

		testPlan.accept(visitor);

		var inOrder = inOrder(visitor);

		inOrder.verify(visitor).preVisitContainer(TestIdentifier.from(engineDescriptor));
		inOrder.verify(visitor).visit(TestIdentifier.from(engineDescriptor));
		inOrder.verify(visitor).preVisitContainer(TestIdentifier.from(container));
		inOrder.verify(visitor).visit(TestIdentifier.from(container));
		inOrder.verify(visitor).visit(TestIdentifier.from(test1));
		inOrder.verify(visitor).postVisitContainer(TestIdentifier.from(container));
		inOrder.verify(visitor).postVisitContainer(TestIdentifier.from(engineDescriptor));

		inOrder.verify(visitor).preVisitContainer(TestIdentifier.from(engineDescriptor2));
		inOrder.verify(visitor).visit(TestIdentifier.from(engineDescriptor2));
		inOrder.verify(visitor).visit(TestIdentifier.from(test2));
		inOrder.verify(visitor).visit(TestIdentifier.from(test3));
		inOrder.verify(visitor).postVisitContainer(TestIdentifier.from(engineDescriptor2));
	}

}
