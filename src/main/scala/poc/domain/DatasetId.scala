package poc.domain

import java.{util => ju}

case class DatasetId(value: ju.UUID) extends AnyVal
case class Dataset(
    id: DatasetId,
    tags: Set[String],
    attributes: Map[String, String]
)
