/*
 * Copyright (C) 2018 Knot.x Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.knotx.databridge.api.reactivex;

import io.knotx.databridge.api.DataSourceAdapterProxy;
import io.knotx.databridge.api.DataSourceAdapterRequest;
import io.knotx.databridge.api.DataSourceAdapterResponse;
import io.knotx.dataobjects.ClientResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class AbstractDataSourceAdapterProxy implements DataSourceAdapterProxy {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDataSourceAdapterProxy.class);

  protected abstract Single<DataSourceAdapterResponse> processRequest(DataSourceAdapterRequest message);

  public void process(DataSourceAdapterRequest request, Handler<AsyncResult<DataSourceAdapterResponse>> result) {
    processRequest(request)
        .subscribe(
            adapterResponse -> result.handle(Future.succeededFuture(adapterResponse)),
            error -> {
              LOGGER.error("Error happened during Adapter Request processing", error);
              result.handle(Future.succeededFuture(getErrorResponse(error)));
            }
        );
  }

  /**
   * Method generates error {@link DataSourceAdapterResponse} in case of processing failure.
   *
   * @param error - error that occurred.
   * @return - error response (e.g. with 500 status code and other info).
   */
  protected DataSourceAdapterResponse getErrorResponse(Throwable error) {
    return new DataSourceAdapterResponse().setResponse(new ClientResponse()
        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
        .setBody(Buffer.buffer(errorBody(error))));
  }

  private String errorBody(Throwable error){
    return jsonErrorBody(error).encode();
  }

  private JsonObject jsonErrorBody(Throwable error){
    return new JsonObject().put("errorMessage", error.getMessage());
  }
}
