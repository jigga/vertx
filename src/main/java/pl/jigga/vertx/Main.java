package pl.jigga.vertx;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.uritemplate.UriTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Main {

  public static void main(String[] args) {
    final var vertx = Vertx.vertx();
    final var serverOptions = new HttpServerOptions()
      .setSsl(true)
      .setPfxKeyCertOptions(new PfxOptions()
        .setPath("keystore.p12") // Path to your keystore file
        .setPassword("adminadmin")
      );
    final var server = vertx.createHttpServer(serverOptions);

    final var router = Router.router(vertx);

    final var payload = new byte[70 * 1024 * 1024];
    router
      .route("/api")
      .handler(ctx -> {
        final var response = ctx.response();

        response.putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
        response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(payload.length));

        response.send(Buffer.buffer(Unpooled.wrappedBuffer(payload)));
      });

    server.requestHandler(router)
      .exceptionHandler(throwable -> throwable.printStackTrace(System.err))
      .listen(8080)
      .toCompletionStage()
      .toCompletableFuture()
      .join();
    System.out.println("Server running...");


    final var executor = Executors.newFixedThreadPool(33);
    final var tasksFutures = Stream.generate(() -> new GetPayloadTask(1000))
      .limit(1000)
      .parallel()
      .map(task -> CompletableFuture.runAsync(task, executor))
      .toArray(CompletableFuture[]::new);
//    final var mainFuture = CompletableFuture.allOf(CompletableFuture.runAsync(new GetPayloadTask(10)));
    final var mainFuture = CompletableFuture.allOf(tasksFutures);
    System.out.println("Awaiting mainFuture to complete");
    mainFuture.join();
    /*System.out.println("Closing the server");
    final var serverClosedFuture = new CompletableFuture<Void>();
    final var vertxClosedFuture = new CompletableFuture<Void>();
    server.close(event -> {
      System.out.println("Server closed event received - " + event);
      if (event.succeeded()) {
        serverClosedFuture.complete(null);
      } else {
        serverClosedFuture.completeExceptionally(event.cause());
      }
    });
    vertx.close(event -> {
      System.out.println("Vertx closed event received - " + event);
      if (event.succeeded()) {
        vertxClosedFuture.complete(null);
      } else {
        vertxClosedFuture.completeExceptionally(event.cause());
      }
    });
    CompletableFuture.allOf(serverClosedFuture, vertxClosedFuture).join();
    executor.shutdownNow();*/
  }

  static class GetPayloadTask implements Runnable {

    private static final AtomicInteger taskIdGenerator = new AtomicInteger();

    private static final WebClientOptions webClientOptions = new WebClientOptions()
      .setSsl(true)
      .setTrustAll(true)
      .setVerifyHost(false);
    private static final WebClient webClient = WebClient.create(Vertx.vertx(), webClientOptions);;

    private final int iterations;
    private final int taskId;
    private final HttpClient client;

    public GetPayloadTask(int iterations) {
      this.iterations = iterations;
      this.taskId = taskIdGenerator.incrementAndGet();
      this.client = HttpClient.newHttpClient();
      /*final var vertx = Vertx.vertx();
      final var webClientOptions = new WebClientOptions()
        .setSsl(true)
        .setTrustAll(true)
        .setVerifyHost(false);
      this.webClient = WebClient.create(vertx, webClientOptions);*/
    }

    @Override
    public void run() {
      System.out.println(taskId + " - running the GetPayloadTask");
      runWithWebClient();
    }

    void runWithHttpClient() {
      for (int i = 0; i < iterations; i++) {
        final var request = HttpRequest.newBuilder()
          .uri(URI.create("https://localhost:8080/api"))
          .GET()
          .header(HttpHeaders.ACCEPT.toString(), HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
          .timeout(Duration.ofSeconds(30))
          .build();
        try {
          System.out.println(taskId + " - " + i + " - sending request - " + request);
          final var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
          System.out.println(taskId + " - " + i + " - got response - " + response + ", response size is " + response.body().length);
          System.out.println(HttpHeaders.CONTENT_LENGTH + ": " + response.headers().allValues(HttpHeaders.CONTENT_LENGTH.toString()));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    void runWithWebClient() {
      webClient.get(8080, "localhost", "/api")
        .ssl(true)
        .send(result -> {
          if (result.succeeded()) {
            final var response = result.result();
            System.out.println("Received response with status code: " + response.statusCode());
            System.out.println("Response body length: " + response.body().length());
          } else {
            System.out.println("Something went wrong: " + result.cause().getMessage());
            result.cause().printStackTrace(System.err);
          }
        });
    }
  }

}
