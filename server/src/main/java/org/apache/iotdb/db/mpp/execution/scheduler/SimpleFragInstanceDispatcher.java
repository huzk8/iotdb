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

package org.apache.iotdb.db.mpp.execution.scheduler;

import org.apache.iotdb.db.mpp.sql.planner.plan.FragmentInstance;
import org.apache.iotdb.mpp.rpc.thrift.InternalService;

import org.apache.thrift.TException;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class SimpleFragInstanceDispatcher implements IFragInstanceDispatcher {

  private final ExecutorService executor;

  public SimpleFragInstanceDispatcher(ExecutorService exeutor) {
    this.executor = exeutor;
  }

  @Override
  public Future<FragInstanceDispatchResult> dispatch(List<FragmentInstance> instances) {
    return executor.submit(
        () -> {
          try {
            for (FragmentInstance instance : instances) {
              InternalService.Client client =
                  InternalServiceClientFactory.getInternalServiceClient(
                      instance.getHostEndpoint().getIp(), instance.getHostEndpoint().getPort());
              // TODO: (xingtanzjr) add request construction
              client.sendFragmentInstance(null);
            }
          } catch (TException e) {
            // TODO: (xingtanzjr) add more details
            return new FragInstanceDispatchResult(false);
          }
          return new FragInstanceDispatchResult(true);
        });
  }

  @Override
  public void abort() {}
}
