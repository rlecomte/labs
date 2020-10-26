package poc

import cats.data.Kleisli
import cats.mtl.{Raise, Handle}

package object tooling {
  type RaiseError[F[_]] = Raise[F, AppError]
  type HandleError[F[_]] = Handle[F, AppError]
  type StateBuilder[S, E] =
    Kleisli[Either[AppError, ?], (Option[S], Event[E]), Option[S]]

  type CommandProcessor[F[_], C, S, E] =
    Kleisli[F, (Option[S], C), List[NewEvent[E]]]

  case class AggregateActionPlan[F[_], C, S, E](
      stateBuilder: StateBuilder[S, E],
      commandProcessor: CommandProcessor[F, C, S, E]
  )
}
