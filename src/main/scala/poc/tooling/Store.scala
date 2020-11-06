package poc.tooling

import cats.implicits._
import java.{util => ju}
import cats.instances.map
import cats.Applicative
import cats.Traverse
import cats.Eval
import cats.effect.IO
import cats.Foldable
import cats.~>
import io.circe.Json
import io.circe.Decoder
import io.circe.Encoder
import cats.ApplicativeError
import fs2._
import cats.mtl.MonadPartialOrder

trait Store[F[_]] {

  def getAggregateEvents[A](
      id: AggregateId
  )(implicit decoder: Decoder[A]): fs2.Stream[F, Event[A]]

  def getAggregateEventFromVersion[A](id: AggregateId, version: Version)(
      implicit decoder: Decoder[A]
  ): F[Option[Event[A]]]

  def getAll[A](
      seqNum: SeqNum,
      eventTypes: List[String]
  )(implicit decoder: Decoder[A]): fs2.Stream[F, Event[A]]

  def register[A](
      id: AggregateId,
      version: Option[Version],
      events: List[NewEvent[A]]
  )(implicit
      encoder: Encoder[A]
  ): F[Boolean]

  def mapK[G[_]](fk: (F ~> G)): Store[G] = {
    Store.mapK(this)(fk)
  }
}

object Store {
  def mapK[F[_], G[_]](
      store: Store[F]
  )(fk: (F ~> G)): Store[G] = {
    new Store[G] {
      override def getAggregateEvents[A](id: AggregateId)(implicit
          decoder: Decoder[A]
      ): Stream[G, Event[A]] = {
        store.getAggregateEvents[A](id).translate(fk)
      }

      override def getAggregateEventFromVersion[A](
          id: AggregateId,
          version: Version
      )(implicit decoder: Decoder[A]): G[Option[Event[A]]] = {
        fk(store.getAggregateEventFromVersion[A](id, version))
      }

      override def getAll[A](seqNum: SeqNum, eventTypes: List[String])(implicit
          decoder: Decoder[A]
      ): Stream[G, Event[A]] = {
        store.getAll[A](seqNum, eventTypes).translate(fk)
      }

      override def register[A](
          id: AggregateId,
          version: Option[Version],
          events: List[NewEvent[A]]
      )(implicit encoder: Encoder[A]): G[Boolean] = {
        fk(store.register[A](id, version, events))
      }
    }
  }
}
