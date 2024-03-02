/*
 * Copyright 2015-2024 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.console.tasks;

import static org.apiguardian.api.API.Status.INTERNAL;

import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apiguardian.api.API;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.commons.util.ClassLoaderUtils;
import org.junit.platform.console.options.Details;
import org.junit.platform.console.options.TestConsoleOutputOptions;
import org.junit.platform.console.options.TestDiscoveryOptions;
import org.junit.platform.console.options.Theme;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

/**
 * @since 1.0
 */
@API(status = INTERNAL, since = "1.0")
public class ConsoleTestExecutor {

	private final TestDiscoveryOptions discoveryOptions;
	private final TestConsoleOutputOptions outputOptions;
	private final Supplier<Launcher> launcherSupplier;

	public ConsoleTestExecutor(TestDiscoveryOptions discoveryOptions, TestConsoleOutputOptions outputOptions) {
		this(discoveryOptions, outputOptions, LauncherFactory::create);
	}

	// for tests only
	ConsoleTestExecutor(TestDiscoveryOptions discoveryOptions, TestConsoleOutputOptions outputOptions,
			Supplier<Launcher> launcherSupplier) {
		this.discoveryOptions = discoveryOptions;
		this.outputOptions = outputOptions;
		this.launcherSupplier = launcherSupplier;
	}

	public void discover(PrintWriter out) {
		new CustomContextClassLoaderExecutor(createCustomClassLoader()).invoke(() -> {
			discoverTests(out);
			return null;
		});
	}

	public TestExecutionSummary execute(PrintWriter out, Optional<Path> reportsDir) {
		return new CustomContextClassLoaderExecutor(createCustomClassLoader()) //
				.invoke(() -> executeTests(out, reportsDir));
	}

	private void discoverTests(PrintWriter out) {
		Launcher launcher = launcherSupplier.get();
		Optional<DetailsPrintingListener> commandLineTestPrinter = createDetailsPrintingListener(out);

		LauncherDiscoveryRequest discoveryRequest = new DiscoveryRequestCreator().toDiscoveryRequest(discoveryOptions);
		TestPlan testPlan = launcher.discover(discoveryRequest);

		commandLineTestPrinter.ifPresent(printer -> printer.listTests(testPlan));
		if (outputOptions.getDetails() != Details.NONE) {
			printFoundTestsSummary(out, testPlan);
		}
	}

	private static void printFoundTestsSummary(PrintWriter out, TestPlan testPlan) {
		SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
		summaryListener.testPlanExecutionStarted(testPlan);
		TestExecutionSummary summary = summaryListener.getSummary();

		out.printf("%n[%10d containers found ]%n[%10d tests found      ]%n%n", summary.getContainersFoundCount(),
			summary.getTestsFoundCount());
		out.flush();
	}

	private TestExecutionSummary executeTests(PrintWriter out, Optional<Path> reportsDir) {
		Launcher launcher = launcherSupplier.get();
		SummaryGeneratingListener summaryListener = registerListeners(out, reportsDir, launcher);

		LauncherDiscoveryRequest discoveryRequest = new DiscoveryRequestCreator().toDiscoveryRequest(discoveryOptions);
		launcher.execute(discoveryRequest);

		TestExecutionSummary summary = summaryListener.getSummary();
		if (summary.getTotalFailureCount() > 0 || outputOptions.getDetails() != Details.NONE) {
			printSummary(summary, out);
		}

		return summary;
	}

	private Optional<ClassLoader> createCustomClassLoader() {
		List<Path> additionalClasspathEntries = discoveryOptions.getExistingAdditionalClasspathEntries();
		if (!additionalClasspathEntries.isEmpty()) {
			URL[] urls = additionalClasspathEntries.stream().map(this::toURL).toArray(URL[]::new);
			ClassLoader parentClassLoader = ClassLoaderUtils.getDefaultClassLoader();
			ClassLoader customClassLoader = URLClassLoader.newInstance(urls, parentClassLoader);
			return Optional.of(customClassLoader);
		}
		return Optional.empty();
	}

	private URL toURL(Path path) {
		try {
			return path.toUri().toURL();
		}
		catch (Exception ex) {
			throw new JUnitException("Invalid classpath entry: " + path, ex);
		}
	}

	private SummaryGeneratingListener registerListeners(PrintWriter out, Optional<Path> reportsDir, Launcher launcher) {
		// always register summary generating listener
		SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
		launcher.registerTestExecutionListeners(summaryListener);
		// optionally, register test plan execution details printing listener
		createDetailsPrintingListener(out).ifPresent(launcher::registerTestExecutionListeners);
		// optionally, register XML reports writing listener
		createXmlWritingListener(out, reportsDir).ifPresent(launcher::registerTestExecutionListeners);
		return summaryListener;
	}

	private Optional<DetailsPrintingListener> createDetailsPrintingListener(PrintWriter out) {
		ColorPalette colorPalette = getColorPalette();
		Theme theme = outputOptions.getTheme();
		switch (outputOptions.getDetails()) {
			case SUMMARY:
				// summary listener is always created and registered
				return Optional.empty();
			case FLAT:
				return Optional.of(new FlatPrintingListener(out, colorPalette));
			case TREE:
				return Optional.of(new TreePrintingListener(out, colorPalette, theme));
			case VERBOSE:
				return Optional.of(new VerboseTreePrintingListener(out, colorPalette, 16, theme));
			case TESTFEED:
				return Optional.of(new TestFeedPrintingListener(out, colorPalette));
			default:
				return Optional.empty();
		}
	}

	private ColorPalette getColorPalette() {
		if (outputOptions.isAnsiColorOutputDisabled()) {
			return ColorPalette.NONE;
		}
		if (outputOptions.getColorPalettePath() != null) {
			return new ColorPalette(outputOptions.getColorPalettePath());
		}
		if (outputOptions.isSingleColorPalette()) {
			return ColorPalette.SINGLE_COLOR;
		}
		return ColorPalette.DEFAULT;
	}

	private Optional<TestExecutionListener> createXmlWritingListener(PrintWriter out, Optional<Path> reportsDir) {
		return reportsDir.map(it -> new LegacyXmlReportGeneratingListener(it, out));
	}

	private void printSummary(TestExecutionSummary summary, PrintWriter out) {
		// Otherwise the failures have already been printed in detail
		if (EnumSet.of(Details.NONE, Details.SUMMARY, Details.TREE).contains(outputOptions.getDetails())) {
			summary.printFailuresTo(out);
		}
		summary.printTo(out);
	}

	@FunctionalInterface
	public interface Factory {
		ConsoleTestExecutor create(TestDiscoveryOptions discoveryOptions, TestConsoleOutputOptions outputOptions);
	}
}
