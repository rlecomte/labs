package poc.tooling

import io.circe.generic.semiauto._

case class EventMetadata(correlationId: Option[Long] = None)

object EventMetadata {
  implicit val newEventMetadataDecoder = deriveDecoder[EventMetadata]
  implicit val newEventMetadataEncoder = deriveEncoder[EventMetadata]
}
