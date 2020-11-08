package poc.api

import cats.effect.concurrent.Ref
import cats.effect.IO
import poc.projection.DatasetProjection.State
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import poc.tooling.SeqNum

object ProjectionRoutes {

  def projectionRoutes(ref: Ref[IO, (State, SeqNum)]): HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._
    HttpRoutes.of[IO] {
      case GET -> Root / "dataset-attributes" =>
        for {
          s <- ref.get
          r <- Ok(s._1)
        } yield r
    }
  }
}
