/*
 * Sonar Groovy Plugin
 * Copyright (C) 2010 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.groovy.surefire;

import org.fest.assertions.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.resources.*;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.test.IsResource;
import org.sonar.plugins.groovy.foundation.Groovy;
import org.sonar.plugins.groovy.surefire.api.SurefireUtils;

import java.io.File;
import java.net.URISyntaxException;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Created by iwarapter
 */
public class GroovySurefireSensorTest {

	private DefaultFileSystem fs = new DefaultFileSystem();
	private Project project;
	private ResourcePerspectives perspectives;
	private GroovySurefireSensor surefireSensor;
	private PathResolver pathResolver = new PathResolver();

	@Before
	public void before() {
		project = mock(Project.class);
		fs = new DefaultFileSystem();
		DefaultInputFile groovyFile = new DefaultInputFile("src/org/foo/grvy");
		groovyFile.setLanguage(Groovy.KEY);
		fs.add(groovyFile);
		perspectives = mock(ResourcePerspectives.class);

		GroovySurefireParser parser = spy(new GroovySurefireParser(perspectives, project, fs));

		doAnswer(new Answer<Resource>() {
			@Override
			public Resource answer(InvocationOnMock invocation) throws Throwable {
				return resource((String) invocation.getArguments()[0]);
			}
		}).when(parser).getUnitTestResource(anyString());

		surefireSensor = new GroovySurefireSensor(parser, mock(Settings.class),fs, pathResolver);
	}

	@Test
	public void should_execute_if_filesystem_contains_groovy_files() {
		surefireSensor = new GroovySurefireSensor(new GroovySurefireParser(perspectives, project, fs), mock(Settings.class), fs, pathResolver);
    	Assertions.assertThat(surefireSensor.shouldExecuteOnProject(project)).isTrue();
	}

	@Test
	public void should_not_execute_if_filesystem_does_not_contains_groovy_files() {
		surefireSensor = new GroovySurefireSensor(new GroovySurefireParser(perspectives, project, fs), mock(Settings.class), new DefaultFileSystem(), pathResolver);
		Assertions.assertThat(surefireSensor.shouldExecuteOnProject(project)).isFalse();
	}

	@Test
	public void shouldNotFailIfReportsNotFound() {
		Settings settings = mock(Settings.class);
		when(settings.getString(SurefireUtils.SUREFIRE_REPORTS_PATH_PROPERTY)).thenReturn("unknown");

		ProjectFileSystem projectFileSystem = mock(ProjectFileSystem.class);
		when(projectFileSystem.resolvePath("unknown")).thenReturn(new File("src/test/resources/unknown"));

		Project project = mock(Project.class);
		when(project.getFileSystem()).thenReturn(projectFileSystem);

		GroovySurefireSensor surefireSensor = new GroovySurefireSensor(mock(GroovySurefireParser.class), settings, fs, pathResolver);
		surefireSensor.analyse(project, mockContext());
	}


	@Test
	public void shouldHandleTestSuiteDetails() throws URISyntaxException {
		SensorContext context = mockContext();

		surefireSensor.collect(context, new File(getClass().getResource(
				"/org/sonar/plugins/groovy/surefire/SurefireSensorTest/shouldHandleTestSuiteDetails/").toURI()));

		// 3 classes, 6 measures by class
		verify(context, times(3)).saveMeasure(argThat(new IsResource(Scopes.FILE, Qualifiers.UNIT_TEST_FILE)),
				eq(CoreMetrics.SKIPPED_TESTS), anyDouble());
		verify(context, times(3)).saveMeasure(argThat(new IsResource(Scopes.FILE, Qualifiers.UNIT_TEST_FILE)),
				eq(CoreMetrics.TESTS), anyDouble());
		verify(context, times(18)).saveMeasure(argThat(new IsResource(Scopes.FILE, Qualifiers.UNIT_TEST_FILE)),
				any(Metric.class), anyDouble());

		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest")), eq(CoreMetrics.TESTS), eq(4d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest")), eq(CoreMetrics.TEST_EXECUTION_TIME),
				eq(111d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest")), eq(CoreMetrics.TEST_FAILURES), eq(1d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest")), eq(CoreMetrics.TEST_ERRORS), eq(1d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest")), eq(CoreMetrics.SKIPPED_TESTS), eq(0d));

		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest2")), eq(CoreMetrics.TESTS), eq(2d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest2")), eq(CoreMetrics.TEST_EXECUTION_TIME), eq(2d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest2")), eq(CoreMetrics.TEST_FAILURES), eq(0d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest2")), eq(CoreMetrics.TEST_ERRORS), eq(0d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest2")), eq(CoreMetrics.SKIPPED_TESTS), eq(0d));

		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest3")), eq(CoreMetrics.TESTS), eq(1d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest3")), eq(CoreMetrics.TEST_EXECUTION_TIME),
				eq(16d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest3")), eq(CoreMetrics.TEST_FAILURES), eq(0d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest3")), eq(CoreMetrics.TEST_ERRORS), eq(0d));
		verify(context).saveMeasure(eq(resource("org.sonar.core.ExtensionsFinderTest3")), eq(CoreMetrics.SKIPPED_TESTS), eq(1d));

	}

	@Test
	public void shouldSaveErrorsAndFailuresInXML() throws URISyntaxException {
		SensorContext context = mockContext();
		surefireSensor.collect(context, new File(getClass().getResource(
				"/org/sonar/plugins/groovy/surefire/SurefireSensorTest/shouldSaveErrorsAndFailuresInXML/").toURI()));

		// 1 classes, 6 measures by class
		verify(context, times(1)).saveMeasure(argThat(new IsResource(Scopes.FILE, Qualifiers.UNIT_TEST_FILE)),
			eq(CoreMetrics.SKIPPED_TESTS), anyDouble());

		verify(context, times(1)).saveMeasure(argThat(new IsResource(Scopes.FILE, Qualifiers.UNIT_TEST_FILE)),
			eq(CoreMetrics.TESTS), anyDouble());
		verify(context, times(6)).saveMeasure(argThat(new IsResource(Scopes.FILE, Qualifiers.UNIT_TEST_FILE)),
			any(Metric.class), anyDouble());
	}

	@Test
	public void shouldManageClassesWithDefaultPackage() throws URISyntaxException {
		SensorContext context = mockContext();
		surefireSensor.collect(context, new File(getClass().getResource(
				"/org/sonar/plugins/groovy/surefire/SurefireSensorTest/shouldManageClassesWithDefaultPackage/").toURI()));

		verify(context).saveMeasure(resource("NoPackagesTest"), CoreMetrics.TESTS, 2d);
	}

	@Test
	public void successRatioIsZeroWhenAllTestsFail() throws URISyntaxException {
		SensorContext context = mockContext();
		surefireSensor.collect(context, new File(getClass().getResource(
				"/org/sonar/plugins/groovy/surefire/SurefireSensorTest/successRatioIsZeroWhenAllTestsFail/").toURI()));

		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TESTS), eq(2d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_FAILURES), eq(1d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_ERRORS), eq(1d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_SUCCESS_DENSITY), eq(0d));
	}

	@Test
	public void measuresShouldNotIncludeSkippedTests() throws URISyntaxException {
		SensorContext context = mockContext();
		surefireSensor.collect(context, new File(getClass().getResource(
				"/org/sonar/plugins/groovy/surefire/SurefireSensorTest/measuresShouldNotIncludeSkippedTests/").toURI()));

		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TESTS), eq(2d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_FAILURES), eq(1d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_ERRORS), eq(0d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.SKIPPED_TESTS), eq(1d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_SUCCESS_DENSITY), eq(50d));

	}

	@Test
	public void noSuccessRatioIfNoTests() throws URISyntaxException {
		SensorContext context = mockContext();
		surefireSensor.collect(context, new File(getClass().getResource(
				"/org/sonar/plugins/groovy/surefire/SurefireSensorTest/noSuccessRatioIfNoTests/").toURI()));

		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TESTS), eq(0d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_FAILURES), eq(0d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_ERRORS), eq(0d));
		verify(context).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.SKIPPED_TESTS), eq(2d));
		verify(context, never()).saveMeasure(eq(resource("org.sonar.Foo")), eq(CoreMetrics.TEST_SUCCESS_DENSITY), anyDouble());
	}

	@Test
	public void ignoreSuiteAsInnerClass() throws URISyntaxException {
		SensorContext context = mockContext();
		surefireSensor.collect(context, new File(getClass().getResource(
				"/org/sonar/plugins/groovy/surefire/SurefireSensorTest/ignoreSuiteAsInnerClass/").toURI()));

		// ignore TestHandler$Input.xml
		verify(context).saveMeasure(eq(resource("org.apache.shindig.protocol.TestHandler")), eq(CoreMetrics.TESTS), eq(0.0));
		verify(context).saveMeasure(eq(resource("org.apache.shindig.protocol.TestHandler")), eq(CoreMetrics.SKIPPED_TESTS), eq(1.0));

	}

	private org.sonar.api.resources.File resource(String key) {
		org.sonar.api.resources.File resource = org.sonar.api.resources.File.create(key);
		resource.setQualifier(Qualifiers.UNIT_TEST_FILE);
		return resource;
	}

	private SensorContext mockContext() {
		SensorContext context = mock(SensorContext.class);
		when(context.isIndexed(any(Resource.class), eq(false))).thenReturn(true);
		return context;
	}
}