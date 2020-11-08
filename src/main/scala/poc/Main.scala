package poc

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.effect.IOApp
import cats.effect.{ExitCode, IO}
import cats.Monad
import cats.effect.Timer
import cats.effect.Sync
import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder}
import cats.data.EitherT
import poc.domain.CustomEnumAlg
import doobie.util.transactor.Transactor
import java.{util => ju}
import doobie.util.pos.Pos
import poc.domain.CustomEnum.CustomEnumEventPayload
import scala.concurrent.duration._
import poc.projection.DatasetProjection
import cats.effect.Bracket
import poc.projection.DatasetProjection.ProjectionError

import cats.implicits._
import fs2._
import tooling._
import cats.mtl._
import scala.concurrent.duration._
import org.http4s.server.blaze.BlazeServerBuilder
import poc.api.CustomEnumRoutes
import cats.effect.Resource
import doobie.util.ExecutionContexts
import cats.effect.Blocker
import doobie.hikari.HikariTransactor
import poc.api.Server

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    Deps.deps().use {
      case Deps(store, logger) =>
        for {
          _ <- logger.info("Create state reference")
          ref <- DatasetProjection.createStateRef

          _ <- logger.info("Start projection background process...")
          _ <-
            DatasetProjection
              .run(store, logger, ref)
              .compile
              .drain
              .start

          _ <- logger.info("Start web server...")
          _ <- Server.run(store, logger).compile.drain
        } yield ExitCode.Success
    }
}
