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
      .evalMapAccumulate[F, Option[S], SeqNum](Option.empty[S]) { (s, e) =>
        plan
          .stateBuilder(s, e)
          .fold(E.raise(_), s => F.pure((s, e.seqNum)))
      }
      .last
      .evalMap[F, Unit] {
        case Some((state, lastEventNum)) =>
          for {
            newEvents <- plan.commandProcessor(state, command)
            result <- store.register(newEvents, Some(lastEventNum))
          } yield if (result) F.pure(()) else E.raise(UnconsistentState)
        case None => E.raise(AggregateNotFound)
      }
  }
}
