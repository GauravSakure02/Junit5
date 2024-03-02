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

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableSet;
import static org.apiguardian.api.API.Status.INTERNAL;
import static org.apiguardian.api.API.Status.STABLE;
import static org.junit.platform.commons.util.CollectionUtils.getOnlyElement;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apiguardian.api.API;
import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.commons.util.ToStringBuilder;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestDescriptor.Type;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;

/**
 * Immutable data transfer object that represents a test or container which is
 * usually part of a {@link TestPlan}.
 *
 * @since 1.0
 * @see TestPlan
 */
@API(status = STABLE, since = "1.0")
public final class TestIdentifier implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final ObjectStreamField[] serialPersistentFields = ObjectStreamClass.lookup(
		SerializedForm.class).getFields();

	// These are effectively final but not technically due to late initialization when deserializing
	private /* final */ UniqueId uniqueId;
	private /* final */ UniqueId parentId;
	private /* final */ String displayName;
	private /* final */ String legacyReportingName;
	private /* final */ TestSource source;
	private /* final */ Set<TestTag> tags;
	private /* final */ Type type;

	/**
	 * Factory for creating a new {@link TestIdentifier} from a {@link TestDescriptor}.
	 */
	@API(status = INTERNAL, since = "1.0")
	public static TestIdentifier from(TestDescriptor testDescriptor) {
		Preconditions.notNull(testDescriptor, "TestDescriptor must not be null");
		UniqueId uniqueId = testDescriptor.getUniqueId();
		String displayName = testDescriptor.getDisplayName();
		TestSource source = testDescriptor.getSource().orElse(null);
		Set<TestTag> tags = testDescriptor.getTags();
		Type type = testDescriptor.getType();
		UniqueId parentId = testDescriptor.getParent().map(TestDescriptor::getUniqueId).orElse(null);
		String legacyReportingName = testDescriptor.getLegacyReportingName();
		return new TestIdentifier(uniqueId, displayName, source, tags, type, parentId, legacyReportingName);
	}

	private TestIdentifier(UniqueId uniqueId, String displayName, TestSource source, Set<TestTag> tags, Type type,
			UniqueId parentId, String legacyReportingName) {
		Preconditions.notNull(type, "TestDescriptor.Type must not be null");
		this.uniqueId = uniqueId;
		this.parentId = parentId;
		this.displayName = displayName;
		this.source = source;
		this.tags = copyOf(tags);
		this.type = type;
		this.legacyReportingName = legacyReportingName;
	}

	private Set<TestTag> copyOf(Set<TestTag> tags) {
		switch (tags.size()) {
			case 0:
				return emptySet();
			case 1:
				return singleton(getOnlyElement(tags));
			default:
				return new LinkedHashSet<>(tags);
		}
	}

	/**
	 * Get the unique ID of the represented test or container as a
	 * {@code String}.
	 *
	 * <p>Uniqueness must be guaranteed across an entire
	 * {@linkplain TestPlan test plan}, regardless of how many engines are used
	 * behind the scenes.
	 *
	 * @return the unique ID for this identifier; never {@code null}
	 */
	public String getUniqueId() {
		return this.uniqueId.toString();
	}

	/**
	 * Get the unique ID of the represented test or container as a
	 * {@code UniqueId}.
	 *
	 * <p>Uniqueness must be guaranteed across an entire
	 * {@linkplain TestPlan test plan}, regardless of how many engines are used
	 * behind the scenes.
	 *
	 * @return the unique ID for this identifier; never {@code null}
	 * @since 5.8
	 */
	@API(status = STABLE, since = "5.8")
	public UniqueId getUniqueIdObject() {
		return this.uniqueId;
	}

	/**
	 * Get the unique ID of this identifier's parent as a {@code String}, if
	 * available.
	 *
	 * <p>An identifier without a parent is called a <em>root</em>.
	 *
	 * @return a container for the unique ID for this identifier's parent;
	 * never {@code null} though potentially <em>empty</em>
	 */
	public Optional<String> getParentId() {
		return getParentIdObject().map(UniqueId::toString);
	}

	/**
	 * Get the unique ID of this identifier's parent as a {@code UniqueId}, if
	 * available.
	 *
	 * <p>An identifier without a parent is called a <em>root</em>.
	 *
	 * @return a container for the unique ID for this identifier's parent;
	 * never {@code null} though potentially <em>empty</em>
	 * @since 5.8
	 */
	@API(status = STABLE, since = "5.8")
	public Optional<UniqueId> getParentIdObject() {
		return Optional.ofNullable(this.parentId);
	}

	/**
	 * Get the display name of the represented test or container.
	 *
	 * <p>A <em>display name</em> is a human-readable name for a test or
	 * container that is typically used for test reporting in IDEs and build
	 * tools. Display names may contain spaces, special characters, and emoji,
	 * and the format may be customized by {@link org.junit.platform.engine.TestEngine
	 * TestEngines} or potentially by end users as well. Consequently, display
	 * names should never be parsed; rather, they should be used for display
	 * purposes only.
	 *
	 * @return the display name for this identifier; never {@code null} or blank
	 * @see #getSource()
	 * @see org.junit.platform.engine.TestDescriptor#getDisplayName()
	 */
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Get the name of this identifier in a format that is suitable for legacy
	 * reporting infrastructure &mdash; for example, for reporting systems built
	 * on the Ant-based XML reporting format for JUnit 4.
	 *
	 * <p>The default implementation delegates to {@link #getDisplayName()}.
	 *
	 * @return the legacy reporting name; never {@code null} or blank
	 * @see org.junit.platform.engine.TestDescriptor#getLegacyReportingName()
	 * @see org.junit.platform.reporting.legacy.LegacyReportingUtils
	 */
	@SuppressWarnings("JavadocReference")
	public String getLegacyReportingName() {
		return this.legacyReportingName;
	}

	/**
	 * Get the underlying descriptor type.
	 *
	 * @return the underlying descriptor type; never {@code null}
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Determine if this identifier represents a test.
	 *
	 * @return {@code true} if the underlying descriptor type represents a test,
	 * {@code false} otherwise
	 * @see Type#isTest()
	 */
	public boolean isTest() {
		return getType().isTest();
	}

	/**
	 * Determine if this identifier represents a container.
	 *
	 * @return {@code true} if the underlying descriptor type represents a container,
	 * {@code false} otherwise
	 * @see Type#isContainer()
	 */
	public boolean isContainer() {
		return getType().isContainer();
	}

	/**
	 * Get the {@linkplain TestSource source} of the represented test
	 * or container, if available.
	 *
	 * @see TestSource
	 */
	public Optional<TestSource> getSource() {
		return Optional.ofNullable(this.source);
	}

	/**
	 * Get the set of {@linkplain TestTag tags} associated with the represented
	 * test or container.
	 *
	 * @see TestTag
	 */
	public Set<TestTag> getTags() {
		return unmodifiableSet(this.tags);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TestIdentifier) {
			TestIdentifier that = (TestIdentifier) obj;
			return Objects.equals(this.uniqueId, that.uniqueId);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return this.uniqueId.hashCode();
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringBuilder(this)
				.append("uniqueId", this.uniqueId)
				.append("parentId", this.parentId)
				.append("displayName", this.displayName)
				.append("legacyReportingName", this.legacyReportingName)
				.append("source", this.source)
				.append("tags", this.tags)
				.append("type", this.type)
				.toString();
		// @formatter:on
	}

	private void writeObject(ObjectOutputStream s) throws IOException {
		SerializedForm serializedForm = new SerializedForm(this);
		serializedForm.serialize(s);
	}

	private void readObject(ObjectInputStream s) throws ClassNotFoundException, IOException {
		SerializedForm serializedForm = SerializedForm.deserialize(s);
		uniqueId = UniqueId.parse(serializedForm.uniqueId);
		displayName = serializedForm.displayName;
		source = serializedForm.source;
		tags = serializedForm.tags;
		type = serializedForm.type;
		parentId = UniqueId.parse(serializedForm.parentId);
		legacyReportingName = serializedForm.legacyReportingName;
	}

	/**
	 * Represents the serialized output of {@code TestIdentifier}. The fields on this
	 * class match the fields that {@code TestIdentifier} had prior to 5.8.
	 */
	private static class SerializedForm implements Serializable {

		private static final long serialVersionUID = 1L;

		private final String uniqueId;
		private final String parentId;
		private final String displayName;
		private final String legacyReportingName;
		private final TestSource source;
		@SuppressWarnings({ "serial", "RedundantSuppression" }) // always used with serializable implementation (see TestIdentifier#copyOf())
		private final Set<TestTag> tags;
		private final Type type;

		SerializedForm(TestIdentifier testIdentifier) {
			this.uniqueId = testIdentifier.uniqueId.toString();
			this.parentId = testIdentifier.parentId.toString();
			this.displayName = testIdentifier.displayName;
			this.legacyReportingName = testIdentifier.legacyReportingName;
			this.source = testIdentifier.source;
			this.tags = testIdentifier.tags;
			this.type = testIdentifier.type;
		}

		@SuppressWarnings("unchecked")
		private SerializedForm(ObjectInputStream.GetField fields) throws IOException {
			this.uniqueId = (String) fields.get("uniqueId", null);
			this.parentId = (String) fields.get("parentId", null);
			this.displayName = (String) fields.get("displayName", null);
			this.legacyReportingName = (String) fields.get("legacyReportingName", null);
			this.source = (TestSource) fields.get("source", null);
			this.tags = (Set<TestTag>) fields.get("tags", null);
			this.type = (Type) fields.get("type", null);
		}

		void serialize(ObjectOutputStream s) throws IOException {
			ObjectOutputStream.PutField fields = s.putFields();
			fields.put("uniqueId", uniqueId);
			fields.put("parentId", parentId);
			fields.put("displayName", displayName);
			fields.put("legacyReportingName", legacyReportingName);
			fields.put("source", source);
			fields.put("tags", tags);
			fields.put("type", type);
			s.writeFields();
		}

		static SerializedForm deserialize(ObjectInputStream s) throws ClassNotFoundException, IOException {
			ObjectInputStream.GetField fields = s.readFields();
			return new SerializedForm(fields);
		}
	}

}
