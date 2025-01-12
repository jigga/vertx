package pl.jigga.vertx.server;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;

@SuppressWarnings("unused")
public class ServerVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {

    final var serverOptionsJson = config().getJsonObject("httpServerOptions");
    final var serverOptions = new HttpServerOptions(serverOptionsJson);
    System.out.println(serverOptions.toJson());

    final var payload = new byte[70 * 1024 * 1024];
    final var router = Router.router(vertx);
    final var apiRoute = router
      .route("/api")
      .handler(ctx -> {
        final var response = ctx.response();
        response.exceptionHandler(throwable -> {
          System.out.println("Request failed - " + throwable.getMessage());
        });
        response.putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
        response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(payload.length));
        response.send(Buffer.buffer(Unpooled.wrappedBuffer(payload)));
      });

    vertx.createHttpServer(serverOptions)
      .exceptionHandler(throwable -> {
        throwable.printStackTrace(System.err);
      })
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

}
