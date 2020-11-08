package poc.projection

import poc.tooling.Store
import poc.tooling.SeqNum
import poc.domain.CustomEnum.CustomEnumEventPayload
import poc.tooling.Event
import poc.tooling.Version
import poc.domain.CustomEnum._
import poc.domain.DatasetId
import poc.tooling.AggregateId
import cats.effect.Sync
import cats.mtl.Raise
import cats.effect.Timer
import cats.~>
import io.chrisdavenport.log4cats.Logger

import poc.domain.CustomEnum
import poc.domain.MandatoryEnum
import cats.data.EitherT
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.Monad
import io.circe.Encoder
import io.circe.Json
import scala.concurrent.duration._
import cats.implicits._
import fs2._

object DatasetProjection {

  class ThrowError[F[_], E <: Throwable](implicit F: Sync[F])
      extends (EitherT[F, E, *] ~> F) {

    def apply[A](fa: EitherT[F, E, A]): F[A] = {
      fa.value.flatMap {
        case Left(err) => F.raiseError(err)
        case Right(a)  => F.pure(a)
      }
    }
  }

  sealed trait ProjectionError extends Exception
  case object CantRetrieveCreationEvent extends ProjectionError

  sealed trait Op
  case class AddMandatoryLabel(label: String, defaultValue: String) extends Op
  case class DeleteLabel(label: String) extends Op
  case class PinValue(datasetId: DatasetId, label: String, value: String)
      extends Op
  case class UnpinValue(datasetId: DatasetId, label: String) extends Op
  case object Ignore extends Op

  type Attributes = Map[String, String]
  case class State(
      mandatories: Attributes,
      datasetAttributes: Map[DatasetId, Attributes]
  ) {
    def addMandatory(label: String, value: String): State = {
      val mand = mandatories + ((label, value))
      val attrs = datasetAttributes.view.mapValues { attr =>
        attr + ((label, value))
      }.toMap

      State(mand, attrs)
    }

    def deleteLabel(label: String): State = {
      val mand = mandatories - label
      val attrs = datasetAttributes.view.mapValues { attr =>
        attr - label
      }.toMap

      State(mand, attrs)
    }

    def pin(id: DatasetId, label: String, value: String): State = {
      val attrs =
        datasetAttributes.getOrElse(id, mandatories) + ((label, value))
      State(mandatories, datasetAttributes + ((id, attrs)))
    }

    def unpin(id: DatasetId, label: String): State = {
      val attrsOpt = datasetAttributes.get(id).map(attrs => attrs - label)
      attrsOpt
        .map(attrs => State(mandatories, datasetAttributes + ((id, attrs))))
        .getOrElse(this)
    }
  }

  object State {
    implicit val circeEncoder: Encoder[State] = new Encoder[State] {
      def apply(a: State): Json =
        Json.obj(
          "defaultAttributesValues" -> Encoder
            .encodeMap[String, String]
            .apply(a.mandatories),
          "datasetAttributes" -> Encoder
            .encodeMap[String, Attributes]
            .apply(a.datasetAttributes.map { case (k, v) => (k.toString(), v) })
        )
    }
    import org.http4s.circe._
    implicit def stateEntityEncoder = jsonEncoderOf[IO, State]
  }

  def createStateRef = Ref[IO].of((State(Map(), Map()), SeqNum.init))

  def run(
      store: Store[IO],
      logger: Logger[IO],
      state: Ref[IO, (State, SeqNum)]
  )(implicit
      timer: Timer[IO]
  ): fs2.Stream[IO, Unit] = {
    for {
      _ <- Stream.awakeEvery[IO](1.second)
      currentState <- Stream.eval(state.get) // get the current projection state
      newState <-
        // read all unread events from the eventstore and apply it to the projection state
        // As long as there is events to read, the stream will fetch and apply it to the projection.
        Stream
          .unfoldEval(currentState) {
            case (s, pos) =>
              getNextStateWithStreamPosition[EitherT[IO, ProjectionError, *]](
                store.mapK(EitherT.liftK)
              )(s, pos) // apply events to the state from the new position
                .translate(
                  new ThrowError[IO, ProjectionError]
                ) // unlift IO from EitherT : throw error
                .compile // compile stream to IO
                .last // get only last element (the last state)
                .map(r => r.zip(r))
                .handleErrorWith {
                  case projectionError: ProjectionError =>
                    handleProjectionError(logger)(
                      projectionError
                    ).map(_ => None) // log and forget
                  case err =>
                    logger.error(err)(
                      "Unexpected error on dataset projection"
                    ) *> IO.raiseError(err)
                }
          }
          .last
      _ <- newState match {
        // if some : we have successfully read events from store and a new state is available : register it in our Ref
        case Some(t @ (s, _)) =>
          Stream
            .eval(logger.info(s"refresh projection : $s") *> state.set(t))
            .map(_ => s)
        // otherwise keep current projection state
        case None => Stream.eval(IO.pure(currentState._1))
      }
    } yield ()
  }

  def getNextStateWithStreamPosition[F[_]](
      store: Store[F]
  )(state: State, position: SeqNum)(implicit
      F: Monad[F],
      R: Raise[F, ProjectionError]
  ): fs2.Stream[F, (State, SeqNum)] = {
    store
      .getAll[CustomEnumEventPayload](
        position,
        CustomEnum.eventTypeList
      )
      .evalMapAccumulate((state, position)) {
        case ((s, _), e) =>
          for {
            op <- toOpAlgebra(store)(e)
            newPos = e.seqNum
          } yield ((applyOpOnState(s, op), newPos.inc()), ())
      }
      .map(_._1)
  }

  private def applyOpOnState(state: State, op: Op): State = {
    op match {
      case AddMandatoryLabel(label, defaultValue) =>
        state.addMandatory(label, defaultValue)
      case DeleteLabel(label) =>
        state.deleteLabel(label)
      case PinValue(datasetId, label, value) =>
        state.pin(datasetId, label, value)
      case UnpinValue(datasetId, label) =>
        state.unpin(datasetId, label)
      case Ignore => state
    }
  }

  private def toOpAlgebra[F[_]](store: Store[F])(
      event: Event[CustomEnumEventPayload]
  )(implicit
      F: Monad[F],
      E: Raise[F, ProjectionError]
  ): F[Op] = {
    event.payload match {
      case CustomEnumCreated(label, _, _, MandatoryEnum(defaultValue)) =>
        F.pure(AddMandatoryLabel(label = label, defaultValue = defaultValue))

      case CustomEnumDeleted() =>
        for {
          creationEvent <- fetchCreationEvent(store)(event.id)
        } yield DeleteLabel(label = creationEvent.label)

      case CustomEnumPinned(datasetId, value) =>
        for {
          creationEvent <- fetchCreationEvent(store)(event.id)
        } yield PinValue(
          datasetId = datasetId,
          label = creationEvent.label,
          value = value
        )

      case CustomEnumUnpinned(datasetId) =>
        for {
          creationEvent <- fetchCreationEvent(store)(event.id)
        } yield UnpinValue(datasetId = datasetId, label = creationEvent.label)

      case _ => F.pure(Ignore)
    }
  }

  def fetchCreationEvent[F[_]](store: Store[F])(
      aggregateId: AggregateId
  )(implicit
      F: Monad[F],
      E: Raise[F, ProjectionError]
  ): F[CustomEnumCreated] = {
    for {
      creationEventOpt <-
        store
          .getAggregateEventFromVersion[CustomEnumEventPayload](
            aggregateId,
            Version.init
          )
      creationEvent <- creationEventOpt.map(_.payload) match {
        case Some(e @ CustomEnumCreated(_, _, _, _)) => F.pure(e)
        case _                                       => E.raise(CantRetrieveCreationEvent)
      }
    } yield creationEvent
  }

  def handleProjectionError(
      logger: Logger[IO]
  )(projectionError: ProjectionError): IO[Unit] = {
    projectionError match {
      case CantRetrieveCreationEvent =>
        logger.error("Can't retrieve creation event.")
    }
  }
}
