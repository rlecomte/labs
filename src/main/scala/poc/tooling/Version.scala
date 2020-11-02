package poc.tooling

import io.circe._, io.circe.generic.semiauto._

case class Version(value: Long) extends AnyVal {
  def inc(): Version = Version(value + 1)
}

object Version {
  val init = Version(0)

  implicit val versionDecoder: Decoder[Version] =
    Decoder[Long].map(Version.apply)

  implicit val versionEncoder: Encoder[Version] =
    Encoder[Long].contramap(_.value)
}
