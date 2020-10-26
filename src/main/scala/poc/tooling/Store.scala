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

  def getAll(seqNum: Long, eventTypes: Seq[String]): fs2.Stream[F, Event[Json]]

  def register[A](
      id: AggregateId,
      version: Option[Version],
      events: List[NewEvent[A]]
  )(implicit
      encoder: Encoder[A]
  ): F[Boolean]
}
