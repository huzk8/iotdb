/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.tools;

import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.metadata.MetadataConstant;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.qp.physical.sys.ActivateTemplatePlan;
import org.apache.iotdb.db.qp.physical.sys.CreateTemplatePlan;
import org.apache.iotdb.db.qp.physical.sys.CreateTimeSeriesPlan;
import org.apache.iotdb.db.qp.physical.sys.SetTemplatePlan;
import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.db.tools.mlog.MLogParser;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MLogParserTest {

  private String[] storageGroups = new String[] {"root.sg0", "root.sg1", "root.sgcc", "root.sg"};
  private int[] storageGroupIndex = new int[] {0, 1, 3, 4};

  /*
   * For root.sg0, we prepare 50 CreateTimeseriesPlan.
   * For root.sg1, we prepare 50 CreateTimeseriesPlan, 1 DeleteTimeseriesPlan, 1 ChangeTagOffsetPlan and 1 ChangeAliasPlan.
   * For root.sgcc, we prepare 0 plans on timeseries or device or template.
   * For root.sg, we prepare 1 SetTemplatePlan, 1 AutoCreateDevicePlan and 1 ActivateTemplatePlan.
   *
   * For root.ln.cc, we create it and then delete it, thus there's no mlog of root.ln.cc.
   * There' still 1 CreateTemplatePlan in template_log.bin
   *
   * */
  private int[] mlogLineNum = new int[] {50, 53, 0, 3};

  private IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();

  @Before
  public void setUp() {
    EnvironmentUtils.envSetUp();
  }

  @After
  public void tearDown() throws Exception {
    EnvironmentUtils.cleanEnv();
    File file = new File("target" + File.separator + "tmp" + File.separator + "text.mlog");
    file.deleteOnExit();
    file = new File("target" + File.separator + "tmp" + File.separator + "text.snapshot");
    file.deleteOnExit();
  }

  private void prepareData() {
    // prepare data
    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 5; j++) {
        for (int k = 0; k < 10; k++) {
          CreateTimeSeriesPlan plan = new CreateTimeSeriesPlan();
          try {
            plan.setPath(new PartialPath("root.sg" + i + "." + "device" + j + "." + "s" + k));
            plan.setDataType(TSDataType.INT32);
            plan.setEncoding(TSEncoding.PLAIN);
            plan.setCompressor(CompressionType.GZIP);
            IoTDB.schemaProcessor.createTimeseries(plan);
          } catch (MetadataException e) {
            e.printStackTrace();
          }
        }
      }
    }

    try {
      IoTDB.schemaProcessor.setStorageGroup(new PartialPath("root.ln.cc"));
      IoTDB.schemaProcessor.setStorageGroup(new PartialPath("root.sgcc"));
      IoTDB.schemaProcessor.setTTL(new PartialPath("root.sgcc"), 1234L);
      IoTDB.schemaProcessor.deleteTimeseries(new PartialPath("root.sg1.device1.s1"));
      List<PartialPath> paths = new ArrayList<>();
      paths.add(new PartialPath("root.ln.cc"));
      IoTDB.schemaProcessor.deleteStorageGroups(paths);
      Map<String, String> tags = new HashMap<String, String>();
      tags.put("tag1", "value1");
      IoTDB.schemaProcessor.addTags(tags, new PartialPath("root.sg1.device1.s2"));
      IoTDB.schemaProcessor.changeAlias(new PartialPath("root.sg1.device1.s3"), "hello");
    } catch (MetadataException | IOException e) {
      e.printStackTrace();
    }

    try {
      IoTDB.schemaProcessor.setStorageGroup(new PartialPath("root.sg"));
      IoTDB.schemaProcessor.createSchemaTemplate(genCreateSchemaTemplatePlan());
      SetTemplatePlan setTemplatePlan = new SetTemplatePlan("template1", "root.sg");
      IoTDB.schemaProcessor.setSchemaTemplate(setTemplatePlan);
      IoTDB.schemaProcessor.setUsingSchemaTemplate(
          new ActivateTemplatePlan(new PartialPath("root.sg.d1")));
    } catch (MetadataException e) {
      e.printStackTrace();
    }
  }

  private CreateTemplatePlan genCreateSchemaTemplatePlan() {
    List<List<String>> measurementList = new ArrayList<>();
    measurementList.add(Collections.singletonList("s11"));
    measurementList.add(Collections.singletonList("s12"));

    List<List<TSDataType>> dataTypeList = new ArrayList<>();
    dataTypeList.add(Collections.singletonList(TSDataType.INT64));
    dataTypeList.add(Collections.singletonList(TSDataType.DOUBLE));

    List<List<TSEncoding>> encodingList = new ArrayList<>();
    encodingList.add(Collections.singletonList(TSEncoding.RLE));
    encodingList.add(Collections.singletonList(TSEncoding.GORILLA));

    List<List<CompressionType>> compressionTypes = new ArrayList<>();
    compressionTypes.add(Collections.singletonList(CompressionType.SNAPPY));
    compressionTypes.add(Collections.singletonList(CompressionType.SNAPPY));

    List<String> schemaNames = new ArrayList<>();
    schemaNames.add("s11");
    schemaNames.add("s12");

    return new CreateTemplatePlan(
        "template1", schemaNames, measurementList, dataTypeList, encodingList, compressionTypes);
  }

  @Test
  public void testMLogParser() throws Exception {
    prepareData();
    testNonExistingStorageGroupDir("root.ln.cc");

    IoTDB.schemaProcessor.forceMlog();

    testParseStorageGroupLog();

    for (int i = 0; i < storageGroups.length; i++) {
      testParseMLog(storageGroups[i], storageGroupIndex[i], mlogLineNum[i]);
    }

    testParseTemplateLogFile();
  }

  private void testNonExistingStorageGroupDir(String storageGroup) {
    File storageGroupDir =
        new File(
            IoTDBDescriptor.getInstance().getConfig().getSchemaDir()
                + File.separator
                + storageGroup);
    Assert.assertFalse(storageGroupDir.exists());
  }

  private void testParseStorageGroupLog() throws IOException {
    testParseLog(config.getSchemaDir() + File.separator + MetadataConstant.STORAGE_GROUP_LOG, 7);
  }

  private void testParseTemplateLogFile() throws IOException {

    testParseLog(
        IoTDBDescriptor.getInstance().getConfig().getSchemaDir()
            + File.separator
            + MetadataConstant.TEMPLATE_FILE,
        1);
  }

  private void testParseMLog(String storageGroup, int storageGroupId, int expectedLineNum)
      throws IOException {
    testParseLog(
        IoTDBDescriptor.getInstance().getConfig().getSchemaDir()
            + File.separator
            + storageGroup
            + File.separator
            + storageGroupId
            + File.separator
            + MetadataConstant.METADATA_LOG,
        expectedLineNum);
  }

  private void testParseLog(String path, int expectedNum) throws IOException {
    File file = new File("target" + File.separator + "tmp" + File.separator + "text.mlog");
    file.delete();

    MLogParser.parseFromFile(
        path, "target" + File.separator + "tmp" + File.separator + "text.mlog");

    try (BufferedReader reader =
        new BufferedReader(
            new FileReader("target" + File.separator + "tmp" + File.separator + "text.mlog"))) {
      int lineNum = 0;
      List<String> lines = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        lineNum++;
        lines.add(line);
      }
      if (lineNum != expectedNum) {
        for (String content : lines) {
          System.out.println(content);
        }
      }
      Assert.assertEquals(expectedNum, lineNum);
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }
}
