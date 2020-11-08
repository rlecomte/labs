package poc.api

import cats.implicits._
import cats.mtl._
import fs2.Stream
import poc.tooling.Store
import io.chrisdavenport.log4cats.Logger
import cats.effect.IO
import cats.effect.Concurrent
import org.http4s.server.blaze.BlazeServerBuilder
import cats.effect.ContextShift
import cats.effect.Timer

object Server {

  def run(store: Store[IO], logger: Logger[IO])(implicit
      cs: ContextShift[IO],
      timer: Timer[IO]
  ): Stream[IO, Nothing] = {
    import scala.concurrent.ExecutionContext.global
    import org.http4s.implicits._

    val httpApp = CustomEnumRoutes.customEnumRoutes(store, logger).orNotFound
    // With Middlewares in place
    val finalHttpApp =
      org.http4s.server.middleware.Logger.httpApp[IO](true, true)(httpApp)

    for {
      exitCode <- BlazeServerBuilder[IO](global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
