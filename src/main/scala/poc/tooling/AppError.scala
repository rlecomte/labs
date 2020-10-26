package poc.tooling

sealed trait AppError
case class ImpossibleState(msg: String) extends AppError
case class CommandRefused(msg: String) extends AppError
case object UnconsistentState extends AppError
case object AggregateNotFound extends AppError

object AppError {
  def impossibleState(state: Any, event: Any): ImpossibleState = {
    val msg = s"""
      Current event can't be processed with current state :

      [Current State]

      ${state.toString()}

      ===================================

      [Current Event]

      ${event.toString()}

      ===================================
    """
    ImpossibleState(msg)
  }

}
