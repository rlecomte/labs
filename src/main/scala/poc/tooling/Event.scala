package poc.tooling

import cats.{Applicative, Eval, Traverse}
import cats.implicits._
import io.circe._, io.circe.generic.semiauto._
import java.time.ZonedDateTime

case class Event[A](
    id: AggregateId,
    seqNum: SeqNum,
    version: Version,
    aggregateType: String,
    eventType: String,
    payload: A,
    metadata: EventMetadata,
    createdAt: ZonedDateTime
)

object Event {
  implicit val eventTraverse: Traverse[Event] = new Traverse[Event] {
    def traverse[G[_]: Applicative, A, B](
        fa: Event[A]
    )(f: A => G[B]): G[Event[B]] = {
      f(fa.payload).map(b => fa.map(_ => b))
    }

    def foldLeft[A, B](fa: Event[A], b: B)(f: (B, A) => B): B =
      f(b, fa.payload)

    def foldRight[A, B](fa: Event[A], lb: Eval[B])(
        f: (A, Eval[B]) => Eval[B]
    ): Eval[B] = f(fa.payload, lb)

  }

  implicit def eventDecoder[A](implicit
      encoder: Decoder[A]
  ): Decoder[Event[A]] = deriveDecoder[Event[A]]
}
