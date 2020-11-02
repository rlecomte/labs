package poc.domain

import poc.tooling.Store
import poc.tooling.SeqNum
import cats.effect.concurrent.Ref
import poc.domain.CustomEnum.CustomEnumEventPayload
import poc.tooling.Event
import poc.domain.CustomEnum.CustomEnumCommand
import poc.domain.CustomEnum.CustomEnumCreated
import poc.domain.CustomEnum.CustomEnumDeleted
import cats.effect.Sync
import cats.mtl.Raise
import poc.tooling.Version
import cats.Monad
import cats.implicits._
import fs2._
import poc.domain.CustomEnum.CustomEnumPinned
import poc.domain.CustomEnum.CustomEnumUnpinned
import cats.effect.IO
import cats.data.EitherT

object DatasetProjection {

  type Attributes = Map[String, String]
  type State = Map[DatasetId, Attributes]

  sealed trait ProjectionError
  case object CantRetrieveCreationEvent extends ProjectionError

  import cats.mtl._
  type Eff[A] = EitherT[IO, ProjectionError, A]
  private def run(
      store: Store[Eff],
      state: Ref[Eff, (State, SeqNum)]
  ) = {
    Monad[fs2.Stream[Eff, ?]]
      .iterateUntil(runProjection[Eff](store, state))(_.isEmpty)
  }

  // Stream[(State, SeqNum)]
  // init (ref.get)
  //

  private def runProjection[F[_]](
      store: Store[F],
      state: Ref[F, (State, SeqNum)]
  )(implicit
      F: Monad[F],
      E: Raise[F, ProjectionError]
  ): fs2.Stream[F, Option[SeqNum]] = {
    for {
      (currentState, currentPosition) <- fs2.Stream.eval(state.get)
      (newState, nextPosition) <-
        store
          .getAll[CustomEnumEventPayload](
            currentPosition,
            CustomEnum.eventTypeList
          )
          .chunkAll
          .evalScan[F, (State, Option[SeqNum])](
            (currentState, None)
          ) {
            case ((s, p), chunk) =>
              for {
                newState <- chunk.foldLeftM(s)(buildView(store))
              } yield (
                newState,
                chunk.last.map(_.seqNum.inc())
              )
          }
          .lastOr((currentState, None))
      _ <- nextPosition.traverse { p =>
        fs2.Stream.eval(state.set(newState, p))
      }
    } yield nextPosition
  }

  private def buildView[F[_]](store: Store[F])(
      state: State,
      event: Event[CustomEnumEventPayload]
  )(implicit F: Monad[F], E: Raise[F, ProjectionError]): F[State] = {
    event.payload match {
      case CustomEnumCreated(label, _, _, MandatoryEnum(defaultValue)) =>
        F.pure {
          state.view.mapValues { attributes =>
            attributes + ((label, defaultValue))
          }.toMap
        }

      case CustomEnumDeleted() =>
        for {
          creationEventOpt <-
            store
              .getAggregateEventFromVersion[CustomEnumEventPayload](
                event.id,
                Version.init
              )
          creationEvent <- creationEventOpt.map(_.payload) match {
            case Some(e @ CustomEnumCreated(_, _, _, _)) => F.pure(e)
            case _                                       => E.raise(CantRetrieveCreationEvent)
          }
          newState =
            state.view
              .mapValues(attributes => attributes - creationEvent.label)
              .toMap
        } yield newState

      case CustomEnumPinned(datasetId, value) =>
        for {
          creationEventOpt <-
            store
              .getAggregateEventFromVersion[CustomEnumEventPayload](
                event.id,
                Version.init
              )
          creationEvent <- creationEventOpt.map(_.payload) match {
            case Some(e @ CustomEnumCreated(_, _, _, _)) => F.pure(e)
            case _                                       => E.raise(CantRetrieveCreationEvent)
          }

          datasetAttributes = state.getOrElse(datasetId, Map())
          newAttribute = (creationEvent.label, value)
          newState = state + ((datasetId, datasetAttributes + newAttribute))
        } yield newState

      case CustomEnumUnpinned(datasetId) =>
        for {
          creationEventOpt <-
            store
              .getAggregateEventFromVersion[CustomEnumEventPayload](
                event.id,
                Version.init
              )
          creationEvent <- creationEventOpt.map(_.payload) match {
            case Some(e @ CustomEnumCreated(_, _, _, _)) => F.pure(e)
            case _                                       => E.raise(CantRetrieveCreationEvent)
          }
          datasetAttributes = state.getOrElse(datasetId, Map())
          newState =
            state + ((datasetId, datasetAttributes - creationEvent.label))
        } yield newState

      case _ => F.pure(state)
    }
  }
}
