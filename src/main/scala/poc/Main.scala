package poc

import cats.implicits._
import cats.effect.IOApp
import cats.effect.{ExitCode, IO}
import cats.Monad
import cats.effect.Timer
import scala.concurrent.duration._
import cats.effect.Sync
import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder}
import fs2._
import tooling._
import cats.data.EitherT

object Main extends IOApp {
  import cats.mtl._
  type Eff[A] = EitherT[IO, AppError, A]
  val result: IO[Either[AppError, Unit]] =
    CustomEnumController.createEnum[Eff](null).compile.drain.value

  def run(args: List[String]): IO[ExitCode] = {
    fs2
      .Stream(1, 2, 3)
      .mapAccumulate(0) { case (s, i) => (s + i, i) }
      .last
      .evalTap(v => IO(println(s"heyyyy $v")))
      .compile
      .drain
      .map(_ => ExitCode.Success)
  }

  object CustomEnumController {}
}
