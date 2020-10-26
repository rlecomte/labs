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
import _root_.poc.domain.CustomEnumAlg
import doobie.util.transactor.Transactor
import java.{util => ju}
import doobie.util.pos.Pos
import _root_.poc.domain.CustomEnum.CustomEnumEventPayload

object Main extends IOApp {

  import cats.mtl._
  type Eff[A] = EitherT[IO, AppError, A]

  val xa = Transactor.fromDriverManager[Eff](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5434/postgres",
    "postgres",
    "trololo"
  )

  val createEvents: IO[Either[AppError, Unit]] =
    CustomEnumAlg.createEnum[Eff](new PostgresStore(xa)).compile.drain.value

  def run(args: List[String]): IO[ExitCode] = {
    createEvents.map(_ => ExitCode.Success)
    //new PostgresStore(xa)
    //  .getAggregateEvents[CustomEnumEventPayload](
    //    AggregateId(ju.UUID.randomUUID())
    //  )
    //  .compile
    //  .drain
    //  .map(_ => ExitCode.Success)

    //fs2
    //  .Stream(1, 2, 3)
    //  .mapAccumulate(0) { case (s, i) => (s + i, i) }
    //  .last
    //  .evalTap(v => IO(println(s"heyyyy $v")))
    //  .compile
    //  .drain
    //  .map(_ => ExitCode.Success)
  }
}
