package poc.domain

import cats.data.NonEmptyList
import cats.effect.IO
import cats.Foldable
import cats.implicits._
import cats.effect.Sync
import cats.Applicative
import cats.mtl.Raise
import cats.data.Kleisli

import io.circe.{Decoder, Encoder}, io.circe.generic.auto._
import io.circe.syntax._

import poc.tooling._

sealed trait EnumType
case class MandatoryEnum(defaultValue: String) extends EnumType
case class OptionalEnum(defaultValue: Option[String]) extends EnumType
case class CustomEnum(
    id: AggregateId,
    label: String,
    description: String,
    choices: NonEmptyList[String],
    enumType: EnumType,
    deleted: Boolean = false
)

object CustomEnum {

  val customEnumAggregateType = "custom-enum-aggregate"

  val customEnumCreatedEventType = "custom-enum-created-event"
  val customEnumChoicesAddedEventType = "custom-enum-choices-added-event"
  val customEnumDeletedEventType = "custom-enum-deleted-event-type"
  val customEnumPinnedEventType = "custom-enum-pinned-event-type"
  val customEnumUnpinnedEventType = "custom-enum-unpinned-event-type"

  val eventTypeList = List(
    customEnumCreatedEventType,
    customEnumChoicesAddedEventType,
    customEnumDeletedEventType,
    customEnumPinnedEventType,
    customEnumUnpinnedEventType
  )

  sealed trait CustomEnumEventPayload
  case class CustomEnumCreated(
      label: String,
      description: String,
      choices: NonEmptyList[String],
      enumType: EnumType
  ) extends CustomEnumEventPayload
  case class CustomEnumChoicesAdded(choices: NonEmptyList[String])
      extends CustomEnumEventPayload
  case class CustomEnumDeleted() extends CustomEnumEventPayload
  case class CustomEnumPinned(datasetId: DatasetId, value: String)
      extends CustomEnumEventPayload
  case class CustomEnumUnpinned(datasetId: DatasetId)
      extends CustomEnumEventPayload

  implicit val encodeCustomEnum: Encoder[CustomEnumEventPayload] =
    Encoder.instance {
      case created @ CustomEnumCreated(_, _, _, _)  => created.asJson
      case choicesAdded @ CustomEnumChoicesAdded(_) => choicesAdded.asJson
      case deleted @ CustomEnumDeleted()            => deleted.asJson
      case pinned @ CustomEnumPinned(_, _)          => pinned.asJson
      case unpinned @ CustomEnumUnpinned(_)         => unpinned.asJson
    }

  implicit val decodeCustomEnum: Decoder[CustomEnumEventPayload] =
    List[Decoder[CustomEnumEventPayload]](
      Decoder[CustomEnumCreated].widen,
      Decoder[CustomEnumChoicesAdded].widen,
      Decoder[CustomEnumDeleted].widen,
      Decoder[CustomEnumPinned].widen,
      Decoder[CustomEnumUnpinned].widen
    ).reduceLeft(_ or _)

  sealed trait CustomEnumCommand extends Command
  case class CreateCommand(
      aggregateId: AggregateId,
      label: String,
      description: String,
      choices: NonEmptyList[String],
      mandatory: Boolean,
      defaultValue: Option[String]
  ) extends CustomEnumCommand
  case class AddChoicesCommand(
      aggregateId: AggregateId,
      choices: NonEmptyList[String]
  ) extends CustomEnumCommand
  case class PinCommand(
      aggregateId: AggregateId,
      datasetId: DatasetId,
      value: String
  ) extends CustomEnumCommand
  case class UnpinCommand(aggregateId: AggregateId, datasetId: DatasetId)
      extends CustomEnumCommand
  case class DeleteCommand(aggregateId: AggregateId) extends CustomEnumCommand
}
