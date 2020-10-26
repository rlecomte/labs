package poc.tooling

import io.circe._, io.circe.generic.semiauto._

case class SeqNum(value: Long) extends AnyVal

object SeqNum {
  val init = SeqNum(0)

  implicit val seqNumDecoder: Decoder[SeqNum] =
    Decoder[Long].map(SeqNum.apply)

  implicit val seqNumEncoder: Encoder[SeqNum] =
    Encoder[Long].contramap(_.value)
}
