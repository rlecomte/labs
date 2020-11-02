package poc.tooling

import cats.implicits._
import java.{util => ju}
import cats.instances.map
import cats.Applicative
import cats.Traverse
import cats.Eval
import cats.effect.IO
import cats.Foldable
import io.circe.Json
import io.circe.Decoder
import io.circe.Encoder
import cats.ApplicativeError
import fs2._

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
}
