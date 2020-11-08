package poc.tooling

import io.circe._

case class Version(value: Long) extends AnyVal {
  def inc(i: Int = 1): Version = Version(value + i)
}

object Version {
  val init = Version(0)

  implicit val versionDecoder: Decoder[Version] =
    Decoder[Long].map(Version.apply)

  implicit val versionEncoder: Encoder[Version] =
    Encoder[Long].contramap(_.value)
}
