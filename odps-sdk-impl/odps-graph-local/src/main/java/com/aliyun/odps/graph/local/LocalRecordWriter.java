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

package com.aliyun.odps.graph.local;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import com.aliyun.odps.OdpsType;
import com.aliyun.odps.counter.Counter;
import com.aliyun.odps.io.NullWritable;
import com.aliyun.odps.io.Text;
import com.aliyun.odps.io.Writable;
import com.aliyun.odps.io.WritableRecord;
import com.aliyun.odps.local.common.utils.LocalRunUtils;
import com.csvreader.CsvWriter;

public class LocalRecordWriter {

  private CsvWriter writer;
  private boolean closed = false;
  private File outputFile;
  private Counter recordCounter;
  private Counter byteCounter;
  private long count = 0;

  private static final String NULL_TOKEN = "\\N";

  public LocalRecordWriter(File file, Counter recordCounter, Counter byteCounter)
      throws IOException {
    this.recordCounter = recordCounter;
    this.byteCounter = byteCounter;
    Charset encoding = LocalRunContext.getInstance().getEncoding();
    outputFile = new File(file.getAbsolutePath());
    writer = new CsvWriter(file.getAbsolutePath(), ',', encoding);
  }

  public void write(WritableRecord record) throws IOException {
    Writable[] fields = record.getAll();
    String[] vals = new String[fields.length];
    for (int i = 0; i < fields.length; i++) {
      String rawVal = null;
      if ((fields[i] == null || fields[i] instanceof NullWritable)) {
        rawVal = null;
      } else if (record.getFields()[i].getType() == OdpsType.STRING) {
        try {
          rawVal = LocalRunUtils.toReadableString(((Text)fields[i]).getBytes());
        } catch (Exception e) {
          throw new RuntimeException("to readable string failed!" + e);
        }
      } else {
        rawVal = fields[i].toString();
      }
      vals[i] = encodeColumnValue(rawVal);
    }
    writer.writeRecord(vals);
    ++count;
  }

  public void close() throws IOException {
    if (!closed) {
      closed = true;
      writer.close();
      if (recordCounter != null) {
        recordCounter.increment(count);
      }
      if (byteCounter != null) {
        byteCounter.increment(getLen());
      }
    }
  }

  public long getLen() throws IOException {
    return outputFile.length();
  }

  /**
   * Encode:replace \N with "\N", exception column is null.
   */
  private static String encodeColumnValue(String val) {
    if (val == null) {
      return NULL_TOKEN;
    } else {
      return val.replaceAll("\\\\N", "\"\\\\N\"");
    }
  }
}
