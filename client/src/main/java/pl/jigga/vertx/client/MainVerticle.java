package pl.jigga.vertx.client;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    final var numberOfVerticles =
      config().getInteger("numberOfVerticles");
    final var clientVerticleConfig =
      config().getJsonObject("clientVerticleConfig");
    CompletableFuture
      .allOf(
        Stream
          .generate(() -> {
            final var deploymentOptions = new DeploymentOptions()
              .setConfig(clientVerticleConfig);
            return vertx.deployVerticle(ClientVerticle.class, deploymentOptions);
          })
          .limit(numberOfVerticles)
          .map(f -> f.toCompletionStage().toCompletableFuture())
          .toArray(CompletableFuture[]::new)
      )
      .whenComplete((ignored, throwable) -> {
        if (throwable != null) {
          startPromise.fail(throwable);
        } else {
          startPromise.complete();
        }
      });
  }

}
