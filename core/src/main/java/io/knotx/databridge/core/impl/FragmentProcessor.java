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
package io.knotx.databridge.core.impl;

import static io.knotx.databridge.core.DataBridgeKnotProxy.SUPPORTED_FRAGMENT_ID;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

import io.knotx.databridge.core.DataBridgeKnotOptions;
import io.knotx.databridge.core.datasource.DataSourceEntry;
import io.knotx.databridge.core.datasource.DataSourcesEngine;
import io.knotx.dataobjects.KnotContext;
import io.knotx.exceptions.FragmentProcessingException;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.core.Vertx;
import java.util.concurrent.ExecutionException;

public class FragmentProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(FragmentProcessor.class);

  private final DataSourcesEngine serviceEngine;

  public FragmentProcessor(Vertx vertx, DataBridgeKnotOptions options) {
    this.serviceEngine = new DataSourcesEngine(vertx, options);
  }

  public Single<FragmentContext> processSnippet(final FragmentContext fragmentContext,
                                                KnotContext request) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Processing Handlebars snippet {}", fragmentContext.fragment());
    }
    return Observable.just(fragmentContext)
        .flatMap(FragmentContext::services)
        .map(serviceEngine::mergeWithConfiguration)
        .doOnNext(this::traceService)
        .flatMap(serviceEntry -> fetchServiceData(fragmentContext, request, serviceEntry))
        .reduce(new JsonObject(), JsonObject::mergeIn)
        .map(results -> applyData(fragmentContext, results))
        .onErrorReturn(e -> handleError(fragmentContext, request, e));
  }


  private Observable<JsonObject> fetchServiceData(FragmentContext fragmentContext,
      KnotContext request, DataSourceEntry serviceEntry) {
    return fetchServiceData(request, serviceEntry)
        .toObservable()
        .map(serviceEntry::getResultWithNamespaceAsKey)
        .doOnNext(serviceResult -> processStatusCode(serviceResult, fragmentContext, serviceEntry))
        .doOnError(e -> storeErrorInFragmentForService(fragmentContext, serviceEntry.getName(), e));
  }

  private void processStatusCode(JsonObject serviceResult, FragmentContext fragmentContext, DataSourceEntry serviceEntry){
    int statusCode = serviceEngine.retrieveStatusCode(serviceResult);
    if(isInvalid(statusCode)){
      storeErrorInFragment(fragmentContext, new IllegalStateException(String.format("%s data-source error. Status code returned by adapter is %d", serviceEntry.getName(), statusCode)));
    }
  }
  private boolean isInvalid(int statusCode){
    return statusCode >= INTERNAL_SERVER_ERROR.code();
  }
  private Single<JsonObject> fetchServiceData(KnotContext request, DataSourceEntry service) {
    LOGGER.debug("Fetching data from service {} {}", service.getAddress(), service.getParams());
    try {
      return request.getCache()
          .get(service.getCacheKey(), () -> {
            LOGGER.debug("Requesting data from adapter {} with params {}", service.getAddress(),
                service.getParams());
            return serviceEngine.doServiceCall(service, request).cache();
          });
    } catch (ExecutionException e) {
      LOGGER.fatal("Unable to get service data {}", e);
      return Single.error(e);
    }
  }

  private FragmentContext applyData(final FragmentContext fragmentContext,
      JsonObject serviceResult) {
    LOGGER.trace("Applying data to snippet {}", fragmentContext);
    fragmentContext.fragment().context().mergeIn(serviceResult);
    return fragmentContext;
  }

  private void traceService(DataSourceEntry serviceEntry) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Found service call definition: {} {}", serviceEntry.getAddress(),
          serviceEntry.getParams());
    }
  }

  private void storeErrorInFragmentForService(FragmentContext fragmentContext, String serviceName, Throwable e) {
    LOGGER.error("Data Bridge service {} failed. Cause: {}", serviceName, e.getMessage());
    storeErrorInFragment(fragmentContext, e);
  }

  private void storeErrorInFragment(FragmentContext fragmentContext, Throwable e) {
    fragmentContext.fragment().failure(SUPPORTED_FRAGMENT_ID, e);
  }

  private FragmentContext handleError(FragmentContext fragmentContext, KnotContext request, Throwable t) {
    LOGGER.error("Fragment processing failed. Cause:{}\nRequest:\n{}\nFragmentContext:\n{}\n", t.getMessage(), request.getClientRequest(), fragmentContext);
    storeErrorInFragment(fragmentContext, t);
    if (fragmentContext.fragment().fallback().isPresent()) {
      return fragmentContext;
    } else {
      throw new FragmentProcessingException(String.format("Fragment processing failed in %s", SUPPORTED_FRAGMENT_ID), t);
    }
  }

}
