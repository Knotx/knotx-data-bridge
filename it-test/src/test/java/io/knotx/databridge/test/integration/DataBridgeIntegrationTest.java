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
package io.knotx.databridge.test.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static io.knotx.junit5.util.RequestUtil.subscribeToResult_shouldSucceed;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.knotx.dataobjects.ClientRequest;
import io.knotx.dataobjects.Fragment;
import io.knotx.dataobjects.KnotContext;
import io.knotx.junit5.KnotxApplyConfiguration;
import io.knotx.junit5.KnotxExtension;
import io.knotx.junit5.wiremock.KnotxWiremock;
import io.knotx.junit5.wiremock.KnotxWiremockExtension;
import io.knotx.reactivex.proxy.KnotProxy;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(KnotxExtension.class)
public class DataBridgeIntegrationTest {

  private final static String CORE_MODULE_EB_ADDRESS = "knotx.knot.databridge";

  @KnotxWiremock
  protected WireMockServer mockService;

  @Test
  @KnotxApplyConfiguration("bridgeStack.conf")
  public void callDataBridge_validKnotContextResult(
      VertxTestContext context, Vertx vertx)
      throws IOException, URISyntaxException {

    mockDataSource();

    callWithAssertions(context, vertx, "template-engine/one-snippet-fragment/fragment-valid.txt",
        knotContext -> {
          Assertions.assertTrue(
              knotContext.getFragments().iterator().next().context().containsKey("_result"));
          Assertions.assertEquals(
              knotContext.getFragments().iterator().next().context().getJsonObject("_result")
                         .getString("result"), "success");
        });
  }

  @Test
  @KnotxApplyConfiguration("bridgeStack.conf")
  public void callDataBridge_invalidKnotContextResult(
      VertxTestContext context, Vertx vertx)
      throws IOException, URISyntaxException {

    mockInvalidDataSource();
    callWithAssertions(context, vertx, "template-engine/one-snippet-fragment/fragment-invalid.txt", "fallback",
        knotContext -> {
          Assertions.assertTrue(
              knotContext.getFragments().iterator().next().failed());
        });
  }

  private void mockInvalidDataSource() {
    KnotxWiremockExtension
        .stubForServer(mockService, get(urlMatching("/dataSource/invalid/.*"))
            .willReturn(aResponse()
                .withStatus(500)));
  }

  private void mockDataSource() {
    KnotxWiremockExtension
        .stubForServer(mockService, get(urlMatching("/dataSource/http/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")));
  }

  private void callWithAssertions(
      VertxTestContext context, Vertx vertx, String fragmentPath, String fallback,
      Consumer<KnotContext> onSuccess) throws IOException, URISyntaxException {
    KnotContext message = payloadMessage(fragmentPath, fallback);

    rxProcessWithAssertions(context, vertx, onSuccess, message);
  }

  private void callWithAssertions(
      VertxTestContext context, Vertx vertx, String fragmentPath,
      Consumer<KnotContext> onSuccess) throws IOException, URISyntaxException {
    KnotContext message = payloadMessage(fragmentPath, null);

    rxProcessWithAssertions(context, vertx, onSuccess, message);
  }

  private void rxProcessWithAssertions(VertxTestContext context, Vertx vertx,
      Consumer<KnotContext> onSuccess, KnotContext payload) {
    KnotProxy service = KnotProxy.createProxy(vertx, CORE_MODULE_EB_ADDRESS);
    Single<KnotContext> knotContextSingle = service.rxProcess(payload);

    subscribeToResult_shouldSucceed(context, knotContextSingle, onSuccess);
  }

  private KnotContext payloadMessage(String fragmentPath, String fallback) throws IOException, URISyntaxException {
    String fragmentContent = new String(Files.readAllBytes(Paths.get(getClass().getClassLoader()
        .getResource(fragmentPath).toURI())));
    return new KnotContext()
        .setClientRequest(new ClientRequest())
        .setFragments(Collections.singletonList(
            Fragment.snippet(Collections.singletonList("databridge"), fragmentContent, fallback)));
  }
}
