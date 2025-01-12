package pl.jigga.vertx.client;

import static java.lang.System.out;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class ClientVerticle extends AbstractVerticle {

  private WebClient webClient;
  private long timerId;

  @Override
  public void start(Promise<Void> startPromise) {

    final var webClientOptionsJson = config().getJsonObject("webClientOptions");
    final var webClientOptions = new WebClientOptions(webClientOptionsJson);
    out.println(webClientOptions.toJson());
    webClient = WebClient.create(vertx, webClientOptions);

    // Requests per second
    final var requestRate = config().getInteger("requestRate", 100);

    final var requestOptionsJson = config().getJsonObject("requestOptions");
    final var requestOptions = new RequestOptions(requestOptionsJson);

    timerId = vertx.setPeriodic(1000/requestRate, id -> sendRequest(requestOptions));
    startPromise.complete();
  }

  private void sendRequest(final RequestOptions requestOptions) {
    webClient.request(HttpMethod.GET, requestOptions)
      .send()
      .onSuccess(response -> {
        final var message = "Response received - %s, body length - %d"
          .formatted(response.statusCode(), response.body().length());
        out.println(message);
      })
      .onFailure(err -> System.err.println("Request failed: " + err.getMessage()));
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    if (timerId > 0) {
      vertx.cancelTimer(timerId);
    }
    stopPromise.complete();
  }
}
