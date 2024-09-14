package reasoner.config

import agents.reasoning.IReasonerAgent
import core.Predicate
import core.lars.{LarsBasedProgram, ClockTime, LarsProgram}
import reasoner.asp._
import reasoner.asp.clingo.{StreamingClingoInterpreter, ClingoProgramWithLars, ClingoConversion}
import reasoner.common.{PlainLarsToAspMapper, LarsProgramEncoding}
import reasoner.config.EvaluationModifier.EvaluationModifier
import reasoner.config.ReasonerChoice.ReasonerChoice
import reasoner.incremental.atms.Atms
import reasoner.incremental.jtms.algorithms.Jtms
import reasoner.incremental.{IncrementalRuleMaker, IncrementalReasoner, IncrementalReasonerAtms, IncrementalReasonerAgent}
import reasoner.{ResultFilter, ReasonerWithFilter, Reasoner}

import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by FM on 14.05.16.
  */
object BuildReasoner {
  def withProgram(program: LarsProgram) = Configuration(program)
}

case class Configuration(larsProgram: LarsProgram, clockTime: ClockTime = 1 second) {

  def withReasoner(reasonerChoice: ReasonerChoice, evaluationModifier: EvaluationModifier) = ArgumentBasedConfiguration(this).build(reasonerChoice, evaluationModifier)

  def configure() = ReasonerConfiguration(larsProgram, clockTime)

  def withClockTime(clockTime: ClockTime) = Configuration(larsProgram, clockTime)
}

case class ReasonerConfiguration(program: LarsProgram, clockTime: ClockTime) {

  private lazy val larsProgramEncoding = PlainLarsToAspMapper(clockTime)(program)

  def withClingo() = EvaluationModeConfiguration(larsProgramEncoding)

  def withIncremental(): IncrementalConfiguration = IncrementalConfiguration(larsProgramEncoding)

  def withIncrementalAtms(): IncrementalConfigurationAtms = IncrementalConfigurationAtms(larsProgramEncoding)

}

trait WithLarsBasedProgram {
  val larsBasedProgram: LarsBasedProgram
}

case class IncrementalConfiguration(larsProgramEncoding: LarsProgramEncoding, jtms: Jtms = Jtms()) extends WithLarsBasedProgram {

  val larsBasedProgram = larsProgramEncoding

  def withRandom(random: Random) = IncrementalConfiguration(larsProgramEncoding, Jtms(jtms.network, random))

  def withJtms(jtms: Jtms) = IncrementalConfiguration(larsProgramEncoding, jtms)

  def use() = PreparedReasonerConfiguration(
    larsBasedProgram,
    IncrementalReasoner(IncrementalRuleMaker(larsProgramEncoding), jtms),
    larsBasedProgram.intensionalPredicates ++ larsBasedProgram.signalPredicates
  )

  def useAgent(agent: IReasonerAgent) = PreparedReasonerConfiguration(
    larsBasedProgram,
    IncrementalReasonerAgent(IncrementalRuleMaker(larsProgramEncoding), agent),
    larsBasedProgram.intensionalPredicates ++ larsBasedProgram.signalPredicates
  )


}

case class IncrementalConfigurationAtms(larsProgramEncoding: LarsProgramEncoding, atms: Atms = Atms()) extends WithLarsBasedProgram {

  val larsBasedProgram = larsProgramEncoding

  def withAtms(atms: Atms) = IncrementalConfigurationAtms(larsProgramEncoding, atms)

  def use() = PreparedReasonerConfiguration(
    larsBasedProgram,
    IncrementalReasonerAtms(IncrementalRuleMaker(larsProgramEncoding), atms),
    larsBasedProgram.intensionalPredicates ++ larsBasedProgram.signalPredicates
  )
}

case class EvaluationModeConfiguration(larsProgramEncoding: LarsProgramEncoding) extends WithLarsBasedProgram {

  val clingoProgram: ClingoProgramWithLars = ClingoConversion.fromLars(larsProgramEncoding)
  val larsBasedProgram = larsProgramEncoding

  def withDefaultEvaluationMode() = withEvaluationMode(Direct)

  def withEvaluationMode(evaluationMode: EvaluationMode) = {
    val clingoEvaluation = buildEvaluationMode(OneShotClingoEvaluation(clingoProgram, StreamingClingoInterpreter(clingoProgram)), evaluationMode)
    EvaluationStrategyConfiguration(clingoEvaluation)
  }

  private def buildEvaluationMode(clingoEvaluation: ClingoEvaluation, evaluationMode: EvaluationMode) = evaluationMode match {
    case UseFuture(waitingAtMost: Duration) => FutureStreamingAspInterpreter(clingoEvaluation, waitingAtMost)
    case _ => clingoEvaluation
  }
}

case class EvaluationStrategyConfiguration(clingoEvaluation: ClingoEvaluation) {

  val larsBasedProgram: LarsBasedProgram = clingoEvaluation.program

  def usePull() = PreparedReasonerConfiguration(
    larsBasedProgram,
    AspPullReasoner(clingoEvaluation),
    larsBasedProgram.intensionalPredicates ++ larsBasedProgram.signalPredicates
  )

  def usePush() = PreparedReasonerConfiguration(
    larsBasedProgram,
    AspPushReasoner(clingoEvaluation),
    larsBasedProgram.intensionalPredicates ++ larsBasedProgram.signalPredicates
  )

}

case class PreparedReasonerConfiguration(larsBasedProgram: LarsBasedProgram, reasoner: Reasoner, restrictTo: Set[Predicate]) extends WithLarsBasedProgram {

  def withNoFilter() = PreparedReasonerConfiguration(larsBasedProgram, reasoner, larsBasedProgram.allPredicates)
  def withPredicateFilter(restrictTo: Set[Predicate]) = PreparedReasonerConfiguration(larsBasedProgram, reasoner, restrictTo)
  def withIntensionalFilter() = PreparedReasonerConfiguration(larsBasedProgram, reasoner, larsBasedProgram.intensionalPredicates)

  def seal() = ReasonerWithFilter(reasoner, ResultFilter(restrictTo))

}
