package poc.domain

import poc.tooling.AggregateId

case class CustomAttribute(
    id: AggregateId,
    label: String,
    description: String,
    defaultValue: Option[String]
)

object CustomAttribute {
  sealed trait CustomAttributeEvent
  case class CustomAttributeCreated(
      label: String,
      description: String,
      mandatory: Boolean,
      defaultValue: Option[String]
  ) extends CustomAttributeEvent
  case class CustomAttributeDefaultModified(defaultValue: Option[String])
      extends CustomAttributeEvent
  case object CustomAttributeDeleted extends CustomAttributeEvent
  case class CustomAttributePinned(datasetId: DatasetId, value: Option[String])
      extends CustomAttributeEvent
  case class CustomAttributeUnpinned(datasetId: DatasetId)
      extends CustomAttributeEvent
}
