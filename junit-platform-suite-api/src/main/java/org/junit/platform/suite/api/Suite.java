/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.suite.api;

import static org.apiguardian.api.API.Status.STABLE;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;
import org.junit.platform.commons.annotation.Testable;

/**
 * {@code @Suite} marks a class as a test suite on the JUnit Platform.
 *
 * <p>Selector and filter annotations are used to control the contents of the
 * suite. Additionally configuration can be passed to the suite via the
 * configuration annotations.
 *
 * <p>When the {@link IncludeClassNamePatterns @IncludeClassNamePatterns}
 * annotation is not present, the default include pattern
 * {@value org.junit.platform.engine.discovery.ClassNameFilter#STANDARD_INCLUDE_PATTERN}
 * will be used in order to avoid loading classes unnecessarily (see {@link
 * org.junit.platform.engine.discovery.ClassNameFilter#STANDARD_INCLUDE_PATTERN
 * ClassNameFilter#STANDARD_INCLUDE_PATTERN}).
 *
 * <p>By default a suite discovers tests using the configuration parameters
 * explicitly configured by {@link ConfigurationParameter @ConfigurationParameter}
 * and the configuration parameters from the discovery request that discovered
 * the suite. Annotating a suite with
 * {@link DisableParentConfigurationParameters @DisableParentConfigurationParameters}
 * annotation disables the latter as a source of parameters so that only explicit
 * configuration parameters are taken into account.
 *
 * @since 1.8
 * @see SelectClasses
 * @see SelectClasspathResource
 * @see SelectDirectories
 * @see SelectFile
 * @see SelectModules
 * @see SelectPackages
 * @see SelectUris
 * @see IncludeClassNamePatterns
 * @see ExcludeClassNamePatterns
 * @see IncludeEngines
 * @see ExcludeEngines
 * @see IncludePackages
 * @see ExcludePackages
 * @see IncludeTags
 * @see ExcludeTags
 * @see SuiteDisplayName
 * @see ConfigurationParameter
 * @see DisableParentConfigurationParameters
 * @see org.junit.platform.launcher.LauncherDiscoveryRequest
 * @see org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
 * @see org.junit.platform.launcher.Launcher
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Documented
@API(status = STABLE, since = "1.10")
@Testable
public @interface Suite {

	/**
	 * Fail suite if no tests were discovered.
	 *
	 * @since 1.9
	 */
	@API(status = Status.EXPERIMENTAL, since = "1.9")
	boolean failIfNoTests() default true;

}
