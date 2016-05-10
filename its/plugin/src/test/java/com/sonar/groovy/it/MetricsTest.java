/*
 * Groovy :: Integration Tests
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.sonar.groovy.it;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.locator.FileLocation;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.wsclient.Sonar;
import org.sonar.wsclient.services.Measure;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.ResourceQuery;
import static org.fest.assertions.Assertions.assertThat;

public class MetricsTest {

  @ClassRule
  public static final Orchestrator orchestrator = Tests.ORCHESTRATOR;

  private static final String PROJECT = "org.example:example";
  private static final String DIRECTORY = Tests.keyFor(PROJECT, "example");
  private static final String FILE = Tests.keyFor(PROJECT, "example/Greeting.groovy");

  @BeforeClass
  public static void init() {
    orchestrator.executeBuild(Tests.createMavenBuild()
      .setCleanSonarGoals()
      .setPom(FileLocation.of("projects/metrics/pom.xml"))
      .setProperty("sonar.dynamicAnalysis", "false"));
  }

  @Test
  public void project_is_analyzed() {
    Sonar client = orchestrator.getServer().getWsClient();
    assertThat(client.find(new ResourceQuery(PROJECT)).getName()).isEqualTo("My Simple Groovy project");
    assertThat(client.find(new ResourceQuery(PROJECT)).getVersion()).isEqualTo("1.0-SNAPSHOT");
  }

  /*
   * ====================== PROJECT LEVEL ======================
   */

  @Test
  public void projectsMetrics() {
    assertThat(getProjectMeasure("files").getIntValue()).isEqualTo(1);
    assertThat(getProjectMeasure("lines").getIntValue()).isEqualTo(7);

    assertThat(getProjectMeasure("ncloc").getIntValue()).isEqualTo(6);

    assertThat(getProjectMeasure("classes").getIntValue()).isEqualTo(1);
    assertThat(getProjectMeasure("functions").getIntValue()).isEqualTo(1);
  }

  /*
   * ====================== DIRECTORY LEVEL ======================
   */

  @Test
  public void packagesMetrics() {
    assertThat(getPackageMeasure("files").getIntValue()).isEqualTo(1);
    assertThat(getPackageMeasure("lines").getIntValue()).isEqualTo(7);

    assertThat(getPackageMeasure("ncloc").getIntValue()).isEqualTo(6);

    assertThat(getPackageMeasure("classes").getIntValue()).isEqualTo(1);
    assertThat(getPackageMeasure("functions").getIntValue()).isEqualTo(1);
  }

  /*
   * ====================== FILE LEVEL ======================
   */

  @Test
  public void filesMetrics() {
    assertThat(getFileMeasure("files").getIntValue()).isEqualTo(1);
    assertThat(getFileMeasure("lines").getIntValue()).isEqualTo(7);

    assertThat(getFileMeasure("ncloc").getIntValue()).isEqualTo(6);

    assertThat(getFileMeasure("classes").getIntValue()).isEqualTo(1);
    assertThat(getFileMeasure("packages")).isNull();
    assertThat(getFileMeasure("functions").getIntValue()).isEqualTo(1);
  }

  private Measure getFileMeasure(String metricKey) {
    Resource resource = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(FILE, metricKey));
    return resource != null ? resource.getMeasure(metricKey) : null;
  }

  private Measure getPackageMeasure(String metricKey) {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(DIRECTORY, metricKey)).getMeasure(metricKey);
  }

  private Measure getProjectMeasure(String metricKey) {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(PROJECT, metricKey)).getMeasure(metricKey);
  }

}
