/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.reporting.open.xml;

import org.opentest4j.reporting.events.api.ChildElement;
import org.opentest4j.reporting.events.api.Context;
import org.opentest4j.reporting.events.core.Metadata;
import org.opentest4j.reporting.schema.QualifiedName;

class UniqueId extends ChildElement<Metadata, UniqueId> {

	static final QualifiedName ELEMENT = QualifiedName.of(JUnitFactory.NAMESPACE, "uniqueId");

	UniqueId(Context context, String value) {
		super(context, ELEMENT);
		withContent(value);
	}
}
