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

import static org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

class TestFeedPrintingListener implements DetailsPrintingListener {

	private static final String INDENTATION = "\t";
	private static final String STATUS_SEPARATOR = " :: ";

	private final PrintWriter out;
	private final ColorPalette colorPalette;
	private TestPlan testPlan;

	TestFeedPrintingListener(PrintWriter out, ColorPalette colorPalette) {
		this.out = out;
		this.colorPalette = colorPalette;
	}

	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		this.testPlan = testPlan;
	}

	@Override
	public void testPlanExecutionFinished(TestPlan testPlan) {
		this.testPlan = null;
	}

	@Override
	public void executionSkipped(TestIdentifier testIdentifier, String reason) {
		if (shouldPrint(testIdentifier)) {
			String msg = formatTestIdentifier(testIdentifier);
			String indentedReason = indented(String.format("Reason: %s", reason));
			println(Style.SKIPPED,
				String.format("%s" + STATUS_SEPARATOR + "SKIPPED%n" + INDENTATION + "%s", msg, indentedReason));
		}
	}

	@Override
	public void executionStarted(TestIdentifier testIdentifier) {
		if (shouldPrint(testIdentifier)) {
			String msg = formatTestIdentifier(testIdentifier);
			println(Style.NONE, String.format("%s" + STATUS_SEPARATOR + "STARTED", msg));
		}
	}

	@Override
	public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
		TestExecutionResult.Status status = testExecutionResult.getStatus();
		if (testExecutionResult.getThrowable().isPresent()) {
			Style style = Style.valueOf(testExecutionResult);
			String msg = formatTestIdentifier(testIdentifier);
			Throwable throwable = testExecutionResult.getThrowable().get();
			String stacktrace = indented(ExceptionUtils.readStackTrace(throwable));
			println(style,
				String.format("%s" + STATUS_SEPARATOR + "%s%n" + INDENTATION + "%s", msg, status, stacktrace));
		}
		else if (shouldPrint(testIdentifier) || testExecutionResult.getStatus() != SUCCESSFUL) {
			Style style = Style.valueOf(testExecutionResult);
			String msg = formatTestIdentifier(testIdentifier);
			println(style, String.format("%s" + STATUS_SEPARATOR + "%s", msg, status));
		}
	}

	private String formatTestIdentifier(TestIdentifier testIdentifier) {
		return String.join(" > ", collectDisplayNames(testIdentifier.getUniqueIdObject()));
	}

	private void println(Style style, String message) {
		this.out.println(colorPalette.paint(style, message));
	}

	private List<String> collectDisplayNames(UniqueId uniqueId) {
		int size = uniqueId.getSegments().size();
		List<String> displayNames = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			displayNames.add(0, testPlan.getTestIdentifier(uniqueId).getDisplayName());
			if (i < size - 1) {
				uniqueId = uniqueId.removeLastSegment();
			}
		}
		return displayNames;
	}

	private static String indented(String message) {
		return DetailsPrintingListener.indented(message, INDENTATION);
	}

	@Override
	public void listTests(TestPlan testPlan) {
		this.testPlan = testPlan;
		try {
			testPlan.accept(new TestPlan.Visitor() {
				@Override
				public void visit(TestIdentifier testIdentifier) {
					if (shouldPrint(testIdentifier)) {
						println(Style.NONE, formatTestIdentifier(testIdentifier));
					}
				}
			});
		}
		finally {
			this.testPlan = null;
		}
	}

	private static boolean shouldPrint(TestIdentifier testIdentifier) {
		return testIdentifier.isTest();
	}
}
