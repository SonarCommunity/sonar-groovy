/*
 * Sonar Groovy Plugin
 * Copyright (C) 2010 SonarSource
 * sonarqube@googlegroups.com
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
package org.sonar.plugins.groovy.cobertura;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.measures.CoverageMeasuresBuilder;
import org.sonar.api.measures.Measure;
import org.sonar.api.utils.StaxParser;
import org.sonar.api.utils.XmlParserException;

import javax.annotation.CheckForNull;
import javax.xml.stream.XMLStreamException;

import java.io.File;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static java.util.Locale.ENGLISH;
import static org.sonar.api.utils.ParsingUtils.parseNumber;

public class CoberturaReportParser {

  private static final Logger LOG = LoggerFactory.getLogger(CoberturaReportParser.class);

  private final SensorContext context;
  private final FileSystem fileSystem;
  private List<String> sourceDirs = Lists.newArrayList();

  public CoberturaReportParser(final SensorContext context, final FileSystem fileSystem) {
    this.context = context;
    this.fileSystem = fileSystem;
  }

  /**
   * Parse a Cobertura xml report and create measures accordingly
   */
  public void parseReport(File xmlFile) {
    try {
      parseSources(xmlFile);
      parsePackages(xmlFile);
    } catch (XMLStreamException e) {
      throw new XmlParserException(e);
    }
  }

  private void parseSources(File xmlFile) throws XMLStreamException {
    StaxParser sourceParser = new StaxParser(new StaxParser.XmlStreamHandler() {
      @Override
      public void stream(SMHierarchicCursor rootCursor) throws XMLStreamException {
        rootCursor.advance();
        sourceDirs = collectSourceDirs(rootCursor.descendantElementCursor("source"));
      }
    });
    sourceParser.parse(xmlFile);
  }

  private static List<String> collectSourceDirs(SMInputCursor source) throws XMLStreamException {
    List<String> directories = Lists.newLinkedList();
    while (source.getNext() != null) {
      String sourceDir = cleanSourceDir(source.getElemStringValue());
      if (StringUtils.isNotBlank(sourceDir)) {
        directories.add(sourceDir);
      }
    }
    return directories;
  }

  private static String cleanSourceDir(String sourceDir) {
    if (StringUtils.isNotBlank(sourceDir)) {
      return sourceDir.trim();
    }
    return sourceDir;
  }

  private void parsePackages(File xmlFile) throws XMLStreamException {
    StaxParser fileParser = new StaxParser(new StaxParser.XmlStreamHandler() {
      @Override
      public void stream(SMHierarchicCursor rootCursor) throws XMLStreamException {
        rootCursor.advance();
        collectPackageMeasures(rootCursor.descendantElementCursor("package"), sourceDirs);
      }
    });
    fileParser.parse(xmlFile);
  }

  private void collectPackageMeasures(SMInputCursor pack, List<String> sourceDirs) throws XMLStreamException {
    while (pack.getNext() != null) {
      Map<String, CoverageMeasuresBuilder> builderByFilename = Maps.newHashMap();
      collectFileMeasures(pack.descendantElementCursor("class"), builderByFilename);
      handleFileMeasures(builderByFilename, sourceDirs);
    }
  }

  private void handleFileMeasures(Map<String, CoverageMeasuresBuilder> builderByFilename, List<String> sourceDirs) {
    for (Map.Entry<String, CoverageMeasuresBuilder> entry : builderByFilename.entrySet()) {
      String fileKey = entry.getKey();
      InputFile file = getInputFile(fileKey, sourceDirs);
      if (file != null) {
        LOG.info("saving measure for file: " + (file.file() != null ? file.file().getName() : "unknown"));
        for (Measure measure : entry.getValue().createMeasures()) {
          LOG.info("-- measure: " + measure.getMetricKey() + " = " + (measure.hasData() ? measure.getData() : measure.getValue()));
          context.saveMeasure(file, measure);
        }
      } else {
        LOG.warn("File not found: {}", fileKey);
      }
    }
  }

  @CheckForNull
  private InputFile getInputFile(String filename, List<String> sourceDirs) {
    for (String sourceDir : sourceDirs) {
      String fileAbsolutePath = sourceDir + "/" + filename;
      InputFile file = fileSystem.inputFile(fileSystem.predicates().hasAbsolutePath(fileAbsolutePath));
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  private static void collectFileMeasures(SMInputCursor clazz, Map<String, CoverageMeasuresBuilder> builderByFilename) throws XMLStreamException {
    while (clazz.getNext() != null) {
      String fileName = clazz.getAttrValue("filename");
      CoverageMeasuresBuilder builder = builderByFilename.get(fileName);
      if (builder == null) {
        builder = CoverageMeasuresBuilder.create();
        builderByFilename.put(fileName, builder);
      }
      collectFileData(clazz, builder);
    }
  }

  private static void collectFileData(SMInputCursor clazz, CoverageMeasuresBuilder builder) throws XMLStreamException {
    SMInputCursor line = clazz.childElementCursor("lines").advance().childElementCursor("line");
    while (line.getNext() != null) {
      int lineId = Integer.parseInt(line.getAttrValue("number"));
      try {
        builder.setHits(lineId, (int) parseNumber(line.getAttrValue("hits"), ENGLISH));
      } catch (ParseException e) {
        throw new XmlParserException(e);
      }

      String isBranch = line.getAttrValue("branch");
      String text = line.getAttrValue("condition-coverage");
      if (StringUtils.equals(isBranch, "true") && StringUtils.isNotBlank(text)) {
        String[] conditions = StringUtils.split(StringUtils.substringBetween(text, "(", ")"), "/");
        builder.setConditions(lineId, Integer.parseInt(conditions[1]), Integer.parseInt(conditions[0]));
      }
    }
  }
}
