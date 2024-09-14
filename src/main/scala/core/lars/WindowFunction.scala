package core.lars

import common.Util

/**
  * Created by FM on 01.05.16.
  */
sealed trait WindowFunction

sealed trait SlidingWindow[T] extends WindowFunction {
  val windowSize: T
}

/*
    abstractions for *writing* a LARS program.
    in contrast, experimental.evaluation will be at the level of time points. modelling this could amount to
    sealed trait SlidingWindow[Tick](windowSize: Int) extends WindowFunction
 */

case class TimeWindow(windowSize: TimeWindowSize) extends SlidingWindow[TimeWindowSize]

case class TupleWindow(windowSize: TupleCount) extends SlidingWindow[TupleCount]

//case class SlidingSpecificTupleWindow(windowSize: TupleCount) extends SlidingWindow

case class TimeWindowSize(length: Long, unit: TimeUnit = java.util.concurrent.TimeUnit.SECONDS) {
  override def toString() = f"${length} ${Util.timeUnitWritten(unit)}"
}

object TimeWindowSize {
  implicit val ordering = Ordering.by((time: TimeWindowSize) => time.unit.toMillis(time.length))

  implicit def toWindowSize(size: Long): TimeWindowSize = TimeWindowSize(size)

  implicit def toWindowSize(size: Int): TimeWindowSize = TimeWindowSize(size)
}