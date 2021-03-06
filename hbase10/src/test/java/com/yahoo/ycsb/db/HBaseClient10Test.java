/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.yahoo.ycsb.db;

import static org.junit.Assert.*;

import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.StringByteIterator;
import com.yahoo.ycsb.measurements.Measurements;
import com.yahoo.ycsb.workloads.CoreWorkload;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * Integration tests for the YCSB HBase client 1.0, using an HBase minicluster.
 */
public class HBaseClient10Test {

  private final static String COLUMN_FAMILY = "cf";

  private static HBaseTestingUtility testingUtil;
  private HBaseClient10 client;
  private Table table = null;

  /**
   * Creates a mini-cluster for use in these tests.
   *
   * This is a heavy-weight operation, so invoked only once for the test class.
   */
  @BeforeClass
  public static void setUpClass() throws Exception {
    testingUtil = HBaseTestingUtility.createLocalHTU();
    testingUtil.startMiniCluster();
  }

  /**
   * Tears down mini-cluster.
   */
  @AfterClass
  public static void tearDownClass() throws Exception {
    testingUtil.shutdownMiniCluster();
  }

  /**
   * Sets up the mini-cluster for testing.
   *
   * We re-create the table for each test.
   */
  @Before
  public void setUp() throws Exception {
    client = new HBaseClient10();
    client.setConfiguration(new Configuration(testingUtil.getConfiguration()));

    Properties p = new Properties();
    p.setProperty("columnfamily", COLUMN_FAMILY);

    Measurements.setProperties(p);
    final CoreWorkload workload = new CoreWorkload();
    workload.init(p);

    table = testingUtil.createTable(TableName.valueOf(CoreWorkload.table), Bytes.toBytes(COLUMN_FAMILY));

    client.setProperties(p);
    client.init();
  }

  @After
  public void tearDown() throws Exception {
    table.close();
    testingUtil.deleteTable(CoreWorkload.table);
  }

  @Test
  public void testRead() throws Exception {
    final String rowKey = "row1";
    final Put p = new Put(Bytes.toBytes(rowKey));
    p.addColumn(Bytes.toBytes(COLUMN_FAMILY),
        Bytes.toBytes("column1"), Bytes.toBytes("value1"));
    p.addColumn(Bytes.toBytes(COLUMN_FAMILY),
        Bytes.toBytes("column2"), Bytes.toBytes("value2"));
    table.put(p);

    final HashMap<String, ByteIterator> result = new HashMap<String, ByteIterator>();
    final int status = client.read(CoreWorkload.table, rowKey, null, result);
    assertEquals(HBaseClient10.Ok, status);
    assertEquals(2, result.size());
    assertEquals("value1", result.get("column1").toString());
    assertEquals("value2", result.get("column2").toString());
  }

  @Test
  public void testReadMissingRow() throws Exception {
    final HashMap<String, ByteIterator> result = new HashMap<String, ByteIterator>();
    final int status = client.read(CoreWorkload.table, "Missing row", null, result);
    assertEquals(HBaseClient10.NoMatchingRecord, status);
    assertEquals(0, result.size());
  }

  @Test
  public void testScan() throws Exception {
    // Fill with data
    final String colStr = "row_number";
    final byte[] col = Bytes.toBytes(colStr);
    final int n = 10;
    final List<Put> puts = new ArrayList<Put>(n);
    for(int i = 0; i < n; i++) {
      final byte[] key = Bytes.toBytes(String.format("%05d", i));
      final byte[] value = java.nio.ByteBuffer.allocate(4).putInt(i).array();
      final Put p = new Put(key);
      p.addColumn(Bytes.toBytes(COLUMN_FAMILY), col, value);
      puts.add(p);
    }
    table.put(puts);

    // Test
    final Vector<HashMap<String, ByteIterator>> result =
        new Vector<HashMap<String, ByteIterator>>();

    // Scan 5 records, skipping the first
    client.scan(CoreWorkload.table, "00001", 5, null, result);

    assertEquals(5, result.size());
    for(int i = 0; i < 5; i++) {
      final HashMap<String, ByteIterator> row = result.get(i);
      assertEquals(1, row.size());
      assertTrue(row.containsKey(colStr));
      final byte[] bytes = row.get(colStr).toArray();
      final ByteBuffer buf = ByteBuffer.wrap(bytes);
      final int rowNum = buf.getInt();
      assertEquals(i + 1, rowNum);
    }
  }

  @Test
  public void testUpdate() throws Exception{
    final String key = "key";
    final HashMap<String, String> input = new HashMap<String, String>();
    input.put("column1", "value1");
    input.put("column2", "value2");
    final int status = client.insert(CoreWorkload.table, key, StringByteIterator.getByteIteratorMap(input));
    assertEquals(HBaseClient10.Ok, status);

    // Verify result
    final Get get = new Get(Bytes.toBytes(key));
    final Result result = this.table.get(get);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    for(final java.util.Map.Entry<String, String> entry : input.entrySet()) {
      assertEquals(entry.getValue(),
          new String(result.getValue(Bytes.toBytes(COLUMN_FAMILY),
            Bytes.toBytes(entry.getKey()))));
    }
  }

  @Test
  @Ignore("Not yet implemented")
  public void testDelete() {
    fail("Not yet implemented");
  }
}

