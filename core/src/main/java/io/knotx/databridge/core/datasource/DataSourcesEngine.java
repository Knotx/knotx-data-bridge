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
package io.knotx.databridge.core.datasource;


import io.knotx.databridge.api.DataSourceAdapterRequest;
import io.knotx.databridge.api.DataSourceAdapterResponse;
import io.knotx.databridge.core.DataBridgeKnotOptions;
import io.knotx.databridge.core.DataSourceDefinition;
import io.knotx.dataobjects.KnotContext;
import io.knotx.reactivex.databridge.api.DataSourceAdapterProxy;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.core.Vertx;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class DataSourcesEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataSourcesEngine.class);

  private static final String RESULT_NAMESPACE_KEY = "_result";
  private static final String RESPONSE_NAMESPACE_KEY = "_response";

  private final DataBridgeKnotOptions options;

  private final Map<String, DataSourceAdapterProxy> adapters;

  public DataSourcesEngine(Vertx vertx, DataBridgeKnotOptions options) {
    this.options = options;
    this.adapters = new HashMap<>();
    this.options.getDataDefinitions().stream().forEach(
        service -> adapters.put(service.getAdapter(),
            DataSourceAdapterProxy.createProxyWithOptions(
                vertx,
                service.getAdapter(),
                this.options.getDeliveryOptions())
        )
    );
  }

  public Single<JsonObject> doServiceCall(DataSourceEntry serviceEntry, KnotContext knotContext) {
    DataSourceAdapterRequest adapterRequest = new DataSourceAdapterRequest()
        .setRequest(knotContext.getClientRequest())
        .setParams(serviceEntry.getParams());

    return adapters.get(serviceEntry.getAddress()).rxProcess(adapterRequest)
        .map(adapterResponse -> buildResultObject(adapterRequest, adapterResponse));
  }

  public DataSourceEntry mergeWithConfiguration(final DataSourceEntry serviceEntry) {
    Optional<DataSourceDefinition> serviceMetadata = options.getDataDefinitions()
        .stream()
        .filter(service -> serviceEntry.getName().matches(service.getName()))
        .findFirst();

    return serviceMetadata.map(
        metadata ->
            new DataSourceEntry(serviceEntry)
                .setAddress(metadata.getAdapter())
                .mergeParams(metadata.getParams())
                .setCacheKey(metadata.getCacheKey()))
        .orElseThrow(() -> {
          LOGGER.error("Missing service configuration for: {}", serviceEntry.getName());
          return new IllegalStateException("Missing service configuration");
        });
  }

  public int retrieveStatusCode(JsonObject serviceResult){
    return Integer.parseInt(serviceResult.getJsonObject(RESPONSE_NAMESPACE_KEY)
                                         .getString("statusCode"));
  }

  private JsonObject buildResultObject(DataSourceAdapterRequest adapterRequest,
      DataSourceAdapterResponse adapterResponse) {
    JsonObject object = new JsonObject();

    String rawData = adapterResponse.getResponse().getBody().toString().trim();

    if (rawData.charAt(0) == '[') {
      object.put(RESULT_NAMESPACE_KEY, new JsonArray(rawData));
    } else if (rawData.charAt(0) == '{') {
      object.put(RESULT_NAMESPACE_KEY, new JsonObject(rawData));
    } else {
      LOGGER.error("Result of [{} {}] neither Json Array nor Json Object: [{}]",
          adapterRequest.getRequest().getMethod(), adapterRequest.getRequest().getPath(),
          StringUtils.abbreviate(rawData, 15));
    }
    object.put(RESPONSE_NAMESPACE_KEY, new JsonObject()
        .put("statusCode", Integer.toString(adapterResponse.getResponse().getStatusCode())));
    return object;
  }
}
