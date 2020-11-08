package poc.api

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import poc.tooling.Store
import poc.domain.CustomEnum.CreateCommand
import poc.tooling.AggregateId
import cats.effect.IO
import cats.mtl.MonadPartialOrder
import cats.data.EitherT
import cats.data.NonEmptyList
import poc.tooling.Api
import poc.tooling.AppError
import poc.domain.CustomEnumAlg

object CustomEnumRoutes {

  def customEnumRoutes(
      store: Store[IO]
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
            case Left(_)  => BadRequest()
            case Right(_) => Ok()
          }
        } yield httpResult
    }
  }
}
