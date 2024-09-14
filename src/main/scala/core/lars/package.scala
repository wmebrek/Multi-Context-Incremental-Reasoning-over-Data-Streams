package core

/**
  * Created by FM on 29.04.16.
  */
package object lars {

  type TimeVariable = Variable
  type TupleCount = Long
  type Duration = Long

  type ClockTime = scala.concurrent.duration.Duration //temporal duration of a logical time point
  type TimeUnit = scala.concurrent.duration.TimeUnit

  def W(windowSize: TimeWindowSize, temporalModality: TemporalModality, atom: Atom) = WindowAtom(TimeWindow(windowSize), temporalModality, atom)

  type LarsRule = Rule[HeadAtom, ExtendedAtom]

}
