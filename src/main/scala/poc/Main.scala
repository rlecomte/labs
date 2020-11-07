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

object Main extends IOApp {

  val mkXa = IO(
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql://localhost:5434/postgres",
      "postgres",
      "trololo"
    )
  )

  val mkLogger = Slf4jLogger.create[IO]

  def run(args: List[String]): IO[ExitCode] = {

    for {
      logger <- mkLogger
      _ <- logger.info("Start!")

      xa <- mkXa
      store = new PostgresStore(xa)

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
      result <- startServer(store, logger).compile.drain.as(ExitCode.Success)
    } yield result
  }

  def startServer(store: Store[IO], logger: Logger[IO]): Stream[IO, Nothing] = {
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
