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
package org.apache.iotdb.tsfile.read.common.block.column;

import org.apache.iotdb.tsfile.utils.TsPrimitiveType;

import org.openjdk.jol.info.ClassLayout;

import java.util.Optional;

import static io.airlift.slice.SizeOf.sizeOf;
import static org.apache.iotdb.tsfile.read.common.block.column.ColumnUtil.checkValidRegion;

public class LongColumn implements Column {

  private static final int INSTANCE_SIZE = ClassLayout.parseClass(LongColumn.class).instanceSize();
  public static final int SIZE_IN_BYTES_PER_POSITION = Long.BYTES + Byte.BYTES;

  private final int arrayOffset;
  private final int positionCount;
  private final boolean[] valueIsNull;
  private final long[] values;

  private final long retainedSizeInBytes;

  public LongColumn(int positionCount, Optional<boolean[]> valueIsNull, long[] values) {
    this(0, positionCount, valueIsNull.orElse(null), values);
  }

  LongColumn(int arrayOffset, int positionCount, boolean[] valueIsNull, long[] values) {
    if (arrayOffset < 0) {
      throw new IllegalArgumentException("arrayOffset is negative");
    }
    this.arrayOffset = arrayOffset;
    if (positionCount < 0) {
      throw new IllegalArgumentException("positionCount is negative");
    }
    this.positionCount = positionCount;

    if (values.length - arrayOffset < positionCount) {
      throw new IllegalArgumentException("values length is less than positionCount");
    }
    this.values = values;

    if (valueIsNull != null && valueIsNull.length - arrayOffset < positionCount) {
      throw new IllegalArgumentException("isNull length is less than positionCount");
    }
    this.valueIsNull = valueIsNull;

    retainedSizeInBytes = INSTANCE_SIZE + sizeOf(valueIsNull) + sizeOf(values);
  }

  @Override
  public long getLong(int position) {
    checkReadablePosition(position);
    return values[position + arrayOffset];
  }

  @Override
  public TsPrimitiveType getTsPrimitiveType(int position) {
    checkReadablePosition(position);
    return new TsPrimitiveType.TsLong(getLong(position));
  }

  @Override
  public boolean isNull(int position) {
    checkReadablePosition(position);
    return valueIsNull != null && valueIsNull[position + arrayOffset];
  }

  @Override
  public int getPositionCount() {
    return positionCount;
  }

  @Override
  public long getRetainedSizeInBytes() {
    return retainedSizeInBytes;
  }

  @Override
  public Column getRegion(int positionOffset, int length) {
    checkValidRegion(getPositionCount(), positionOffset, length);
    return new LongColumn(positionOffset + arrayOffset, length, valueIsNull, values);
  }

  private void checkReadablePosition(int position) {
    if (position < 0 || position >= getPositionCount()) {
      throw new IllegalArgumentException("position is not valid");
    }
  }
}
