package poc.tooling

import io.circe._

case class SeqNum(value: Long) extends AnyVal {
  def inc(): SeqNum = SeqNum(value + 1)
}

object SeqNum {
  val init = SeqNum(0)

  implicit val seqNumDecoder: Decoder[SeqNum] =
    Decoder[Long].map(SeqNum.apply)

  implicit val seqNumEncoder: Encoder[SeqNum] =
    Encoder[Long].contramap(_.value)
}
