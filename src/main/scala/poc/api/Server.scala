package poc.api

import fs2.Stream
import poc.tooling.Store
import cats.effect.IO
import org.http4s.server.blaze.BlazeServerBuilder
import cats.effect.ContextShift
import cats.effect.Timer
import poc.Config
import cats.effect.concurrent.Ref
import poc.projection.DatasetProjection.State
import poc.tooling.SeqNum

object Server {

  def run(config: Config, store: Store[IO], ref: Ref[IO, (State, SeqNum)])(
      implicit
      cs: ContextShift[IO],
      timer: Timer[IO]
  ): Stream[IO, Nothing] = {
    import scala.concurrent.ExecutionContext.global
    import org.http4s.implicits._
    import cats.implicits._

    val customEnumRoutes = CustomEnumRoutes.customEnumRoutes(store)
    val projectionRoutes = ProjectionRoutes.projectionRoutes(ref)

    val httpApp = (customEnumRoutes <+> projectionRoutes).orNotFound
    // With Middlewares in place
    val finalHttpApp =
      org.http4s.server.middleware.Logger.httpApp[IO](true, true)(httpApp)

    for {
      exitCode <- BlazeServerBuilder[IO](global)
        .bindHttp(config.httpPort, config.httpBind)
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
