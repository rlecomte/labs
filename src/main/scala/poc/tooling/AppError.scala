package poc.tooling

case class UnexpectedError(msg: String) extends Exception

sealed trait AppError
case class ImpossibleState(msg: String) extends AppError
case class CommandRefused(msg: String) extends AppError
case object UnconsistentState extends AppError
case object AggregateNotFound extends AppError

object AppError {}
