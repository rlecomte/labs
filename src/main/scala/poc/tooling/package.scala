package poc

import cats.data.Kleisli
import cats.data.NonEmptyList

package object tooling {
  type StateBuilder[S, E] =
    Kleisli[Either[AppError, ?], (Option[S], Event[E]), Option[S]]

  type CommandHandler[F[_], C, S, E] =
    Kleisli[F, (Option[S], C), NonEmptyList[NewEvent[E]]]

  object States {
    def of[S, E](
        f: (Option[S], Event[E]) => Either[AppError, Option[S]]
    ): StateBuilder[S, E] = {
      Kleisli(f.tupled)
    }
  }

  object Commands {
    trait CommandHandlerBuilder[F[_]] {
      def apply[C, S, E](
          f: (Option[S], C) => F[NonEmptyList[NewEvent[E]]]
      ): CommandHandler[F, C, S, E]
    }

    def handle[F[_]]: CommandHandlerBuilder[F] =
      new CommandHandlerBuilder[F] {
        def apply[C, S, E](
            f: (Option[S], C) => F[NonEmptyList[NewEvent[E]]]
        ): CommandHandler[F, C, S, E] = {
          Kleisli(f.tupled)
        }
      }
  }
}
