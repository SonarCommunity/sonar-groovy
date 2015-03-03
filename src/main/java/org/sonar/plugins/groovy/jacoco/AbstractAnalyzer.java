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
package org.sonar.plugins.groovy.jacoco;

import com.google.common.io.Closeables;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.measures.CoverageMeasuresBuilder;
import org.sonar.api.measures.Measure;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.ResourceUtils;
import org.sonar.api.scan.filesystem.ModuleFileSystem;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.SonarException;

import javax.annotation.CheckForNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

public abstract class AbstractAnalyzer {

  private final ResourcePerspectives perspectives;
  private final ModuleFileSystem moduleFileSystem;
  private final FileSystem fileSystem;
  private final PathResolver pathResolver;

  public AbstractAnalyzer(ResourcePerspectives perspectives, ModuleFileSystem moduleFileSystem, FileSystem fileSystem, PathResolver pathResolver) {
    this.perspectives = perspectives;
    this.moduleFileSystem = moduleFileSystem;
    this.fileSystem = fileSystem;
    this.pathResolver = pathResolver;
  }

  private Resource getResource(ISourceFileCoverage coverage, SensorContext context) {
    String fileRelativePath = coverage.getPackageName() + "/" + coverage.getName();
    Resource resourceInContext = getResource(fileRelativePath, context);
    if (resourceInContext != null && ResourceUtils.isUnitTestClass(resourceInContext)) {
      // Ignore unit tests
      return null;
    }
    return resourceInContext;
  }

  @CheckForNull
  private Resource getResource(String fileRelativePath, SensorContext context) {
    for (File sourceDir : moduleFileSystem.sourceDirs()) {
      String absolutePath = sourceDir.getAbsolutePath() + "/" + fileRelativePath;
      InputFile groovyFile = fileSystem.inputFile(fileSystem.predicates().hasAbsolutePath(absolutePath));
      if (groovyFile != null) {
        return context.getResource(groovyFile);
      }
    }
    return null;
  }

  public final void analyse(Project project, SensorContext context) {
    if (!atLeastOneBinaryDirectoryExists(project)) {
      JaCoCoExtensions.LOG.warn("Project coverage is set to 0% since there is no directories with classes.");
      return;
    }
    String path = getReportPath(project);
    if (path == null) {
      JaCoCoExtensions.LOG.warn("No jacoco coverage execution file found for project " + project.getName() + ".");
      return;
    }
    File jacocoExecutionData = pathResolver.relativeFile(moduleFileSystem.baseDir(), path);

    try {
      readExecutionData(jacocoExecutionData, context);
    } catch (IOException e) {
      throw new SonarException(e);
    }
  }

  private boolean atLeastOneBinaryDirectoryExists(Project project) {
    List<File> binaryDirs = moduleFileSystem.binaryDirs();
    if (binaryDirs == null || binaryDirs.isEmpty()) {
      JaCoCoExtensions.LOG.warn("No binary directories defined for project " + project.getName() + ".");
    }
    for (File binaryDir : binaryDirs) {
      JaCoCoExtensions.LOG.info("\tChecking binary directory: {}", binaryDir.toString());
      if (binaryDir.exists()) {
        return true;
      }
    }
    return false;
  }

  public final void readExecutionData(File jacocoExecutionData, SensorContext context) throws IOException {
    ExecutionDataVisitor executionDataVisitor = new ExecutionDataVisitor();

    if (jacocoExecutionData == null || !jacocoExecutionData.exists() || !jacocoExecutionData.isFile()) {
      JaCoCoExtensions.LOG.warn("Project coverage is set to 0% as no JaCoCo execution data has been dumped: {}", jacocoExecutionData);
    } else {
      JaCoCoExtensions.LOG.info("Analysing {}", jacocoExecutionData);

      InputStream inputStream = null;
      try {
        inputStream = new BufferedInputStream(new FileInputStream(jacocoExecutionData));
        ExecutionDataReader reader = new ExecutionDataReader(inputStream);
        reader.setSessionInfoVisitor(executionDataVisitor);
        reader.setExecutionDataVisitor(executionDataVisitor);
        reader.read();
      } finally {
        Closeables.closeQuietly(inputStream);
      }
    }

    CoverageBuilder coverageBuilder = analyze(executionDataVisitor.getMerged());
    int analyzedResources = 0;
    for (ISourceFileCoverage coverage : coverageBuilder.getSourceFiles()) {
      Resource resource = getResource(coverage, context);
      if (resource != null) {
        CoverageMeasuresBuilder builder = analyzeFile(resource, coverage);
        saveMeasures(context, resource, builder.createMeasures());
        analyzedResources++;
      }
    }
    if (analyzedResources == 0) {
      JaCoCoExtensions.LOG.warn("Coverage information was not collected. Perhaps you forget to include debug information into compiled classes?");
    } else {
      JaCoCoExtensions.LOG.info("No information about coverage per test.");
    }
  }

  private CoverageBuilder analyze(ExecutionDataStore executionDataStore) {
    CoverageBuilder coverageBuilder = new CoverageBuilder();
    Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
    for (File binaryDir : moduleFileSystem.binaryDirs()) {
      analyzeAll(analyzer, binaryDir);
    }
    return coverageBuilder;
  }

  /**
   * Copied from {@link Analyzer#analyzeAll(File)} in order to add logging.
   */
  private void analyzeAll(Analyzer analyzer, File file) {
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        analyzeAll(analyzer, f);
      }
    } else if (file.getName().endsWith(".class")) {
      try {
        analyzer.analyzeAll(file);
      } catch (Exception e) {
        JaCoCoExtensions.LOG.warn("Exception during analysis of file " + file.getAbsolutePath(), e);
      }
    }
  }

  private CoverageMeasuresBuilder analyzeFile(Resource resource, ISourceFileCoverage coverage) {
    CoverageMeasuresBuilder builder = CoverageMeasuresBuilder.create();
    for (int lineId = coverage.getFirstLine(); lineId <= coverage.getLastLine(); lineId++) {
      final int hits;
      ILine line = coverage.getLine(lineId);
      switch (line.getInstructionCounter().getStatus()) {
        case ICounter.FULLY_COVERED:
        case ICounter.PARTLY_COVERED:
          hits = 1;
          break;
        case ICounter.NOT_COVERED:
          hits = 0;
          break;
        case ICounter.EMPTY:
          continue;
        default:
          JaCoCoExtensions.LOG.warn("Unknown status for line {} in {}", lineId, resource);
          continue;
      }
      builder.setHits(lineId, hits);

      ICounter branchCounter = line.getBranchCounter();
      int conditions = branchCounter.getTotalCount();
      if (conditions > 0) {
        int coveredConditions = branchCounter.getCoveredCount();
        builder.setConditions(lineId, conditions, coveredConditions);
      }
    }
    return builder;
  }

  protected abstract void saveMeasures(SensorContext context, Resource resource, Collection<Measure> measures);

  protected abstract String getReportPath(Project project);
}
