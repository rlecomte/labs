package poc.domain

import cats.Applicative
import cats.mtl.Raise
import cats.data.{NonEmptyList, Kleisli}
import cats.implicits._
import cats.effect.Sync
import poc.tooling._
import cats.effect.LiftIO
import cats.effect.IO
import cats.Monad

object CustomEnumAlg {
  import CustomEnum._

  def commandHandler[F[_]](implicit
      F: Applicative[F],
      E: RaiseError[F]
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
        } yield List(
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

      case (Some(s), AddChoicesCommand(id, newChoices)) =>
        F.pure(
          List(
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
          List(
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
          List(
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
          List(
            NewEvent(
              id = id,
              aggregateType = customEnumAggregateType,
              eventType = customEnumDeletedEventType,
              CustomEnumDeleted()
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
              event.id,
              label,
              descr,
              choices,
              defaultValue
            ).some.asRight

          case (Some(s), CustomEnumChoicesAdded(choices)) =>
            s.copy(choices = s.choices |+| choices).some.asRight

          case (Some(s), CustomEnumDeleted()) =>
            s.copy(deleted = true).some.asRight

          case (s, _) => s.asRight
        }
    }

  def createEnum[F[_]](
      store: Store[F]
  )(implicit
      F: LiftIO[F],
      A: Monad[F],
      E: RaiseError[F]
  ): fs2.Stream[F, Unit] = {
    for {
      aggregateId <- fs2.Stream.eval(F.liftIO(AggregateId.newAggregateId[IO]))
      command = CreateCommand(
        aggregateId = aggregateId,
        label = "foo",
        description = "bar",
        choices = NonEmptyList.of("blue pill", "red pill"),
        defaultValue = Some("blue pill"),
        mandatory = true
      )
      _ <- Api.applyCommand(store)(
        command,
        stateBuilder,
        commandHandler
      )
    } yield ()
  }

  def deleteEnum(): Unit = ()

  def pin(): Unit = ()

  def unpin(): Unit = ()
}
