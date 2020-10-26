package poc.tooling

case class SeqNum(value: Long) extends AnyVal

object SeqNum {
  val init = SeqNum(0)
}
