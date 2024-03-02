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

import static org.apiguardian.api.API.Status.INTERNAL;

import java.util.function.Function;
import java.util.function.Supplier;

import org.apiguardian.api.API;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContextException;
import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.engine.support.store.NamespacedHierarchicalStore;
import org.junit.platform.engine.support.store.NamespacedHierarchicalStoreException;

/**
 * @since 5.0
 */
@API(status = INTERNAL, since = "5.0")
public class NamespaceAwareStore implements Store {

	private final NamespacedHierarchicalStore<Namespace> valuesStore;
	private final Namespace namespace;

	public NamespaceAwareStore(NamespacedHierarchicalStore<Namespace> valuesStore, Namespace namespace) {
		this.valuesStore = valuesStore;
		this.namespace = namespace;
	}

	@Override
	public Object get(Object key) {
		Preconditions.notNull(key, "key must not be null");
		return accessStore(() -> this.valuesStore.get(this.namespace, key));
	}

	@Override
	public <T> T get(Object key, Class<T> requiredType) {
		Preconditions.notNull(key, "key must not be null");
		Preconditions.notNull(requiredType, "requiredType must not be null");
		return accessStore(() -> this.valuesStore.get(this.namespace, key, requiredType));
	}

	@Override
	public <K, V> Object getOrComputeIfAbsent(K key, Function<K, V> defaultCreator) {
		Preconditions.notNull(key, "key must not be null");
		Preconditions.notNull(defaultCreator, "defaultCreator function must not be null");
		return accessStore(() -> this.valuesStore.getOrComputeIfAbsent(this.namespace, key, defaultCreator));
	}

	@Override
	public <K, V> V getOrComputeIfAbsent(K key, Function<K, V> defaultCreator, Class<V> requiredType) {
		Preconditions.notNull(key, "key must not be null");
		Preconditions.notNull(defaultCreator, "defaultCreator function must not be null");
		Preconditions.notNull(requiredType, "requiredType must not be null");
		return accessStore(
			() -> this.valuesStore.getOrComputeIfAbsent(this.namespace, key, defaultCreator, requiredType));
	}

	@Override
	public void put(Object key, Object value) {
		Preconditions.notNull(key, "key must not be null");
		accessStore(() -> this.valuesStore.put(this.namespace, key, value));
	}

	@Override
	public Object remove(Object key) {
		Preconditions.notNull(key, "key must not be null");
		return accessStore(() -> this.valuesStore.remove(this.namespace, key));
	}

	@Override
	public <T> T remove(Object key, Class<T> requiredType) {
		Preconditions.notNull(key, "key must not be null");
		Preconditions.notNull(requiredType, "requiredType must not be null");
		return accessStore(() -> this.valuesStore.remove(this.namespace, key, requiredType));
	}

	private <T> T accessStore(Supplier<T> action) {
		try {
			return action.get();
		}
		catch (NamespacedHierarchicalStoreException e) {
			throw new ExtensionContextException(e.getMessage(), e);
		}
	}

}
