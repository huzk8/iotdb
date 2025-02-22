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

package org.apache.iotdb.cluster.query.groupby;

import org.apache.iotdb.cluster.ClusterIoTDB;
import org.apache.iotdb.cluster.client.async.AsyncDataClient;
import org.apache.iotdb.cluster.client.sync.SyncClientAdaptor;
import org.apache.iotdb.cluster.client.sync.SyncDataClient;
import org.apache.iotdb.cluster.config.ClusterConstant;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.rpc.thrift.Node;
import org.apache.iotdb.cluster.rpc.thrift.RaftNode;
import org.apache.iotdb.db.query.aggregation.AggregateResult;
import org.apache.iotdb.db.query.executor.groupby.GroupByExecutor;
import org.apache.iotdb.db.utils.SerializeUtils;
import org.apache.iotdb.tsfile.utils.Pair;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class RemoteGroupByExecutor implements GroupByExecutor {

  private static final Logger logger = LoggerFactory.getLogger(RemoteGroupByExecutor.class);

  private final long executorId;
  private final Node source;
  private final RaftNode header;

  private final List<AggregateResult> results = new ArrayList<>();

  public RemoteGroupByExecutor(long executorId, Node source, RaftNode header) {
    this.executorId = executorId;
    this.source = source;
    this.header = header;
  }

  @Override
  public void addAggregateResult(AggregateResult aggrResult) {
    results.add(aggrResult);
  }

  private void resetAggregateResults() {
    for (AggregateResult result : results) {
      result.reset();
    }
  }

  @Override
  public List<AggregateResult> calcResult(long curStartTime, long curEndTime) throws IOException {

    List<ByteBuffer> aggrBuffers;
    try {
      if (ClusterDescriptor.getInstance().getConfig().isUseAsyncServer()) {
        AsyncDataClient client =
            ClusterIoTDB.getInstance()
                .getAsyncDataClient(source, ClusterConstant.getReadOperationTimeoutMS());
        aggrBuffers =
            SyncClientAdaptor.getGroupByResult(
                client, header, executorId, curStartTime, curEndTime);
      } else {
        SyncDataClient syncDataClient = null;
        try {
          syncDataClient =
              ClusterIoTDB.getInstance()
                  .getSyncDataClient(source, ClusterConstant.getReadOperationTimeoutMS());
          aggrBuffers =
              syncDataClient.getGroupByResult(header, executorId, curStartTime, curEndTime);
        } catch (TException e) {
          // the connection may be broken, close it to avoid it being reused
          syncDataClient.close();
          throw e;
        } finally {
          if (syncDataClient != null) {
            syncDataClient.returnSelf();
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    } catch (Exception e) {
      throw new IOException(e);
    }
    resetAggregateResults();
    if (aggrBuffers != null) {
      for (int i = 0; i < aggrBuffers.size(); i++) {
        AggregateResult result = AggregateResult.deserializeFrom(aggrBuffers.get(i));
        results.get(i).merge(result);
      }
    }
    logger.debug(
        "Fetched group by result from {} of [{}, {}]: {}",
        source,
        curStartTime,
        curEndTime,
        results);
    return results;
  }

  @Override
  public Pair<Long, Object> peekNextNotNullValue(long nextStartTime, long nextEndTime)
      throws IOException {
    ByteBuffer aggrBuffer;
    try {
      if (ClusterDescriptor.getInstance().getConfig().isUseAsyncServer()) {
        AsyncDataClient client =
            ClusterIoTDB.getInstance()
                .getAsyncDataClient(source, ClusterConstant.getReadOperationTimeoutMS());
        aggrBuffer =
            SyncClientAdaptor.peekNextNotNullValue(
                client, header, executorId, nextStartTime, nextEndTime);
      } else {
        SyncDataClient syncDataClient = null;
        try {
          syncDataClient =
              ClusterIoTDB.getInstance()
                  .getSyncDataClient(source, ClusterConstant.getReadOperationTimeoutMS());
          aggrBuffer =
              syncDataClient.peekNextNotNullValue(header, executorId, nextStartTime, nextEndTime);
        } catch (TException e) {
          // the connection may be broken, close it to avoid it being reused
          syncDataClient.close();
          throw e;
        } finally {
          if (syncDataClient != null) {
            syncDataClient.returnSelf();
          }
        }
      }
    } catch (TException e) {
      throw new IOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    }

    Pair<Long, Object> result = null;
    if (aggrBuffer != null) {
      long time = aggrBuffer.getLong();
      Object o = SerializeUtils.deserializeObject(aggrBuffer);
      if (o != null) {
        result = new Pair<>(time, o);
      }
    }
    logger.debug(
        "Fetched peekNextNotNullValue from {} of [{}, {}]: {}",
        source,
        nextStartTime,
        nextEndTime,
        result);
    return result;
  }
}
