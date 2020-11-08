package poc.domain

import cats.Applicative
import cats.mtl.Raise
import cats.implicits._
import poc.tooling._
import cats.data.NonEmptyList

object CustomEnumAlg {
  import CustomEnum._

  def commandHandler[F[_]](implicit
      F: Applicative[F],
      E: Raise[F, AppError]
  ): CommandHandler[
    F,
    CustomEnumCommand,
    CustomEnum,
    CustomEnumEventPayload
  ] =
    Commands.handle[F] {
      case (Some(s), _) if s.deleted =>
        E.raise(
          CommandRefused("custom enum deleted, can't apply command.")
        )

      case (
            None,
            CreateCommand(id, label, desc, choices, mandatory, defaultValue)
          ) =>
        for {
          enumType <- defaultValue match {
            case Some(v) =>
              if (!choices.contains_(v)) {
                E.raise(
                  CommandRefused("Default value should be a valid enum value.")
                )
              } else if (mandatory) {
                F.pure(MandatoryEnum(v))
              } else {
                F.pure(OptionalEnum(defaultValue))
              }
            case None =>
              if (mandatory) {
                E.raise(
                  CommandRefused("Mandatory tag should have a default value.")
                )
              } else {
                F.pure(OptionalEnum(None))
              }
          }
        } yield NonEmptyList.of(
          NewEvent(
            id = id,
            aggregateType = customEnumAggregateType,
            eventType = customEnumCreatedEventType,
            payload = CustomEnumCreated(
              label,
              desc,
              choices,
              enumType
            )
          )
        )

      case (Some(_), AddChoicesCommand(id, newChoices)) =>
        F.pure(
          NonEmptyList.of(
            NewEvent(
              id = id,
              aggregateType = customEnumAggregateType,
              eventType = customEnumChoicesAddedEventType,
              payload = CustomEnumChoicesAdded(newChoices)
            )
          )
        )

      case (Some(_), PinCommand(id, datasetId, value)) =>
        F.pure(
          NonEmptyList.of(
            NewEvent(
              id = id,
              aggregateType = customEnumAggregateType,
              eventType = customEnumPinnedEventType,
              CustomEnumPinned(datasetId = datasetId, value = value)
            )
          )
        )
      case (Some(_), UnpinCommand(id, datasetId)) =>
        F.pure(
          NonEmptyList.of(
            NewEvent(
              id = id,
              aggregateType = customEnumAggregateType,
              eventType = customEnumUnpinnedEventType,
              CustomEnumUnpinned(datasetId = datasetId)
            )
          )
        )

      case (Some(_), DeleteCommand(id)) =>
        F.pure(
          NonEmptyList.of(
            NewEvent(
              id = id,
              aggregateType = customEnumAggregateType,
              eventType = customEnumDeletedEventType,
              CustomEnumDeleted("deleted by user")
            )
          )
        )

      case (_, _) =>
        E.raise(
          CommandRefused("Can't apply the command on the current state.")
        )
    }

  val stateBuilder: StateBuilder[CustomEnum, CustomEnumEventPayload] =
    States.of {
      case (state, event) =>
        (state, event.payload) match {
          case (None, CustomEnumCreated(label, descr, choices, defaultValue)) =>
            CustomEnum(
              id = event.id,
              label = label,
              description = descr,
              choices = choices,
              enumType = defaultValue,
              deleted = false
            ).some.asRight

          case (Some(s), CustomEnumChoicesAdded(choices)) =>
            s.copy(choices = s.choices |+| choices).some.asRight

          case (Some(s), CustomEnumDeleted(_)) =>
            s.copy(deleted = true).some.asRight

          case (s, _) => s.asRight
        }
    }
}
