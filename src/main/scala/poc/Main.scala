package poc

import cats.effect.IOApp
import cats.effect.{ExitCode, IO}
import poc.api.Server
import poc.projection.DatasetProjection
import poc.Config

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    start(Config())
  }

  def start(conf: Config): IO[ExitCode] =
    Deps.deps(conf).use {
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
              .start // unbind process from current thread

          _ <- logger.info("Start web server...")
          _ <- Server.run(conf, store, ref).compile.drain
        } yield ExitCode.Success
    }
}
