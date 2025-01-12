package pl.jigga.vertx.server;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public class ServerVerticle extends AbstractVerticle {

  private static final Semaphore semaphore = new Semaphore(1073741824, true); // 1 Gi

  public ServerVerticle() {
    System.out.println("Constructing new ServerVerticle instance: " + this);
  }

  @Override
  public void start(Promise<Void> startPromise) {

    final var serverOptionsJson = config().getJsonObject("httpServerOptions");
    final var serverOptions = new HttpServerOptions(serverOptionsJson);
    System.out.println(serverOptions.toJson());

    final var payload = new byte[70 * 1024 * 1024];
    final var router = Router.router(vertx);
    final var apiRoute = router
      .route("/api")
      .handler(ctx -> sendResponse(ctx, payload));

    vertx.createHttpServer(serverOptions)
      .exceptionHandler(throwable ->
        throwable.printStackTrace(System.err))
      .requestHandler(router)
      .listen(8888)
      .onComplete(http -> {
        if (http.succeeded()) {
          startPromise.complete();
          System.out.println("HTTP server started on port 8888");
        } else {
          startPromise.fail(http.cause());
        }
      });
  }

  private void sendResponse(final RoutingContext context, final byte[] payload) {
    final var response = context.response();

    if (context.get("id") == null) {
      context.put("id", UUID.randomUUID().toString());
    }
    final String id = context.get("id");

    if (context.get("attempt") == null) {
      context.put("attempt", 0);
    }
    final int attempt = context.get("attempt");
    context.put("attempt", attempt + 1);

    if (semaphore.tryAcquire(payload.length)) {
      System.out.println(id + " - permits acquired, available permits: " + semaphore.availablePermits());
      response.putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
      response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(payload.length));

      // Flag to track if permits have been released
      final var permitsReleased = new AtomicBoolean(false);
      response.end(Buffer.buffer(Unpooled.wrappedBuffer(payload)), endResult -> {
        if (permitsReleased.compareAndSet(false, true)) {
          semaphore.release(payload.length);
          final var message = "%s - response sent, permits released, attempt: %d, available permits: %d"
            .formatted(id, attempt, semaphore.availablePermits());
          System.out.println(message);
        }
      });
    } else {
      System.out.println(id + " - semaphore not acquired, available permits: " + semaphore.availablePermits());
      // exponential backoff
      final var baseDelay = 10;
      final var maxDelay = 1000;
      final var delay = Math.min(baseDelay * (1L << attempt), maxDelay);
      context.vertx().setTimer(delay, timerId -> sendResponse(context, payload));
    }
  }

}
