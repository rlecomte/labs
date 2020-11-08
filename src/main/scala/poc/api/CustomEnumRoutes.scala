package poc.api

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import poc.tooling.Store
import poc.domain.CustomEnum.{CreateCommand, PinCommand}
import poc.tooling.AggregateId
import cats.effect.IO
import cats.data.EitherT
import cats.data.NonEmptyList
import poc.tooling.Api
import poc.tooling.AppError
import poc.domain.CustomEnumAlg
import poc.domain.DatasetId
import org.http4s.circe.CirceEntityDecoder._
import io.circe.generic.auto._
import io.circe.Decoder
import java.{util => ju}

object CustomEnumRoutes {

  def customEnumRoutes(
      store: Store[IO]
  ): HttpRoutes[IO] = {

    val storeK = store.mapK(EitherT.liftK[IO, AppError])

    val dsl = new Http4sDsl[IO] {}
    import dsl._
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "attributes" / "enum" =>
        for {
          aggregateId <- AggregateId.newAggregateId[IO]
          payload <- req.as[CreateEnumPayload]

          command = CreateCommand(
            aggregateId = aggregateId,
            label = payload.label,
            description = payload.description,
            choices = payload.choices,
            defaultValue = payload.defaultValue,
            mandatory = payload.mandatory
          )

          cmdResult <-
            Api
              .applyCommand(storeK)(
                command,
                CustomEnumAlg.stateBuilder,
                CustomEnumAlg.commandHandler
              )
              .compile
              .last
              .value

          httpResult <- cmdResult match {
            case Left(_)         => BadRequest()
            case Right(None)     => BadRequest()
            case Right(Some(id)) => Ok(id.value.toString())
          }
        } yield httpResult

      case req @ PUT -> Root / "attributes" / "enum" / UUIDVar(
            aggregateId
          ) / "pin" =>
        for {
          payload <- req.as[PinPayload]

          command = PinCommand(
            aggregateId = AggregateId(aggregateId),
            datasetId = DatasetId(payload.id),
            value = payload.value
          )

          cmdResult <-
            Api
              .applyCommand(storeK)(
                command,
                CustomEnumAlg.stateBuilder,
                CustomEnumAlg.commandHandler
              )
              .compile
              .last
              .value

          httpResult <- cmdResult match {
            case Left(err)       => BadRequest(err.toString)
            case Right(None)     => BadRequest()
            case Right(Some(id)) => Ok(id.value.toString())
          }

        } yield httpResult
    }
  }
}
case class PinPayload(id: ju.UUID, value: String)

object PinPayload {
  import io.circe.generic.semiauto._

  implicit val pinPayloadDecoder: Decoder[PinPayload] =
    deriveDecoder[PinPayload]
}

case class CreateEnumPayload(
    label: String,
    description: String,
    choices: NonEmptyList[String],
    mandatory: Boolean,
    defaultValue: Option[String]
)

object CreateEnumPayload {
  import io.circe.generic.semiauto._

  implicit val createEnumDecoder: Decoder[CreateEnumPayload] =
    deriveDecoder[CreateEnumPayload]
}
