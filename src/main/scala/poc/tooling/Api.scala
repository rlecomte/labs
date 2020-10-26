package poc.tooling

import cats.implicits._
import cats.Monad
import io.circe.{Decoder, Encoder}

object Api {

  def applyCommand[F[_], C, S, E](store: Store[F])(
      aggregateId: AggregateId,
      command: C,
      plan: AggregateActionPlan[F, C, S, E]
  )(implicit
      DC: Decoder[E],
      EC: Encoder[E],
      E: RaiseError[F],
      F: Monad[F]
  ): fs2.Stream[F, Unit] = {
    store
      .getAggregateEvents[E](aggregateId)
      .evalMapAccumulate[F, Option[S], Version](Option.empty[S]) { (s, e) =>
        plan
          .stateBuilder(s, e)
          .fold(E.raise(_), s => F.pure((s, e.version)))
      }
      .last
      .map {
        case Some((state, lastVersion)) =>
          (state, Some(lastVersion)) // an aggregate already exist
        case None => (None, None) //no trace of the aggregateId in the store
      }
      .evalMap[F, Unit] {
        case (state, lastVersion) =>
          for {
            newEvents <- plan.commandProcessor(state, command)
            result <- store.register(aggregateId, lastVersion, newEvents)
          } yield if (result) F.pure(()) else E.raise(UnconsistentState)
      }
  }
}
