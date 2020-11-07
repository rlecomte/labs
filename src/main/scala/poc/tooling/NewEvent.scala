package poc.tooling

import cats.{Applicative, Eval, Traverse}
import cats.implicits._
import io.circe._, io.circe.generic.semiauto._

case class NewEvent[A](
    id: AggregateId,
    aggregateType: String,
    eventType: String,
    payload: A,
    metadata: EventMetadata = EventMetadata()
)

object NewEvent {
  implicit val newEventTraverse: Traverse[NewEvent] = new Traverse[NewEvent] {
    def traverse[G[_]: Applicative, A, B](
        fa: NewEvent[A]
    )(f: A => G[B]): G[NewEvent[B]] = {
      f(fa.payload).map(b => fa.copy(payload = b))
    }

    def foldLeft[A, B](fa: NewEvent[A], b: B)(f: (B, A) => B): B =
      f(b, fa.payload)

    def foldRight[A, B](fa: NewEvent[A], lb: Eval[B])(
        f: (A, Eval[B]) => Eval[B]
    ): Eval[B] = f(fa.payload, lb)

  }

  implicit def newEventEncoder[A](implicit
      encoder: Encoder[A]
  ): Encoder[NewEvent[A]] = deriveEncoder[NewEvent[A]]
}
