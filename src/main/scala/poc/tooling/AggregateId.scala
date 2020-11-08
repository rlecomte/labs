package poc.tooling

import java.{util => ju}
import io.circe.generic.semiauto._
import cats.effect.Sync

case class AggregateId(value: ju.UUID) extends AnyVal

object AggregateId {
  implicit val aggregateIdDecoder = deriveDecoder[AggregateId]
  implicit val aggregateIdEncoder = deriveEncoder[AggregateId]

  def newAggregateId[F[_]: Sync]: F[AggregateId] =
    Sync[F].delay(AggregateId(ju.UUID.randomUUID()))
}
