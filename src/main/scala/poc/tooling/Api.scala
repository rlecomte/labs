package poc.tooling

import cats.mtl.Raise
import cats.Monad
import io.circe.{Decoder, Encoder}
import cats.implicits._

object Api {

  def applyCommand[F[_], C <: Command, S, E](store: Store[F])(
      command: C,
      stateBuilder: StateBuilder[S, E],
      commandHandler: CommandHandler[F, C, S, E]
  )(implicit
      DC: Decoder[E],
      EC: Encoder[E],
      E: Raise[F, AppError],
      F: Monad[F]
  ): fs2.Stream[F, AggregateId] = {
    store
      .getAggregateEvents[E](command.aggregateId)
      .evalMapAccumulate[F, Option[S], Version](Option.empty[S]) { (s, e) =>
        stateBuilder(s, e)
          .fold(E.raise(_), s => F.pure((s, e.version)))
      }
      .last
      .map {
        case Some((state, lastVersion)) =>
          (state, Some(lastVersion)) // an aggregate already exist
        case None => (None, None) //no trace of the aggregate in the store
      }
      .evalMap[F, AggregateId] {
        case (state, lastVersion) =>
          for {
            newEvents <- commandHandler(state, command)
            result <-
              store.register(command.aggregateId, lastVersion, newEvents)

            aggregateId <- {
              if (result) F.pure(command.aggregateId)
              else E.raise(UnconsistentState)
            }
          } yield aggregateId
      }
  }
}
