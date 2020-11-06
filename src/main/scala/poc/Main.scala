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
import poc.domain.DatasetProjection
import cats.effect.Bracket
import poc.domain.DatasetProjection.ProjectionError

import cats.implicits._
import fs2._
import tooling._
import cats.mtl._
import scala.concurrent.duration._

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
      // create projection state reference in memory
      ref <- DatasetProjection.createStateRef

      // run projection process in background
      _ <- logger.info("Start projection background process...")
      _ <-
        DatasetProjection
          .run(store, ref)
          .compile
          .drain
          .start // unbind process from current thread

      _ <-
        Stream
          .awakeEvery[IO](5.second)
          .evalMap { _ =>
            for {
              s <- ref.get
              _ <- logger.info(s"current projection state : $s")
            } yield ()
          }
          .compile
          .drain
          .start

      // write a command every second
      _ <- logger.info("Start web server...")
      result <- startServer(store, logger)
    } yield ExitCode.Success //result
  }

  def startServer(store: Store[IO], logger: Logger[IO]): IO[ExitCode] = {
    val stream = for {
      _ <- Stream.awakeEvery[EitherT[IO, AppError, *]](1.seconds)
      _ <- Stream.eval {
        logger
          .mapK(EitherT.liftK[IO, AppError])
          .info("Send CreateEnum command...")
      }
      _ <- CustomEnumAlg.createEnum(store.mapK(EitherT.liftK[IO, AppError]))
    } yield ()

    stream.compile.drain.value.flatMap {
      case Right(_) =>
        logger.info("graceful shutdown") *> IO(ExitCode.Success)
      case Left(err) => logger.error(s"Oops $err") *> IO(ExitCode.Error)
    }
  }
}
