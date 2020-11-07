package poc.api

import cats.effect.Sync
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import fs2.Stream
import poc.tooling.Store
import io.chrisdavenport.log4cats.Logger
import poc.domain.CustomEnum.CreateCommand
import poc.tooling.AggregateId
import cats.effect.IO
import cats.effect.LiftIO
import cats.Monad
import cats.mtl.Raise
import poc.tooling.AppError
import org.http4s.Response
import cats.mtl.MonadPartialOrder
import cats.data.EitherT
import cats.data.NonEmptyList
import poc.domain.CustomEnum
import poc.domain.CustomEnumAlg

import fs2._
import cats.mtl._
import poc.tooling.Api

object CustomEnumRoutes {

  def customEnumRoutes(
      store: Store[IO],
      logger: Logger[IO]
  ): HttpRoutes[IO] = {
    val MPG = implicitly[MonadPartialOrder[IO, EitherT[IO, AppError, *]]]

    val dsl = new Http4sDsl[IO] {}
    import dsl._
    HttpRoutes.of[IO] {
      case POST -> Root / "attributes" / "enum" =>
        for {
          aggregateId <- AggregateId.newAggregateId[IO]
          command = CreateCommand(
            aggregateId = aggregateId,
            label = "foo",
            description = "bar",
            choices = NonEmptyList.of("blue pill", "red pill"),
            defaultValue = Some("blue pill"),
            mandatory = true
          )

          cmdResult <-
            Api
              .applyCommand(store.mapK(MPG))(
                command,
                CustomEnumAlg.stateBuilder,
                CustomEnumAlg.commandHandler
              )
              .compile
              .drain
              .value

          httpResult <- cmdResult match {
            case Left(err) => BadRequest()
            case Right(_)  => Ok()
          }
        } yield httpResult
    }
  }
}
