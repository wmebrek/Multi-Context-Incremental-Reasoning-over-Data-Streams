package reasoner.incremental

import agents.reasoning.IReasonerAgent
import core._
import core.asp.NormalRule
import core.lars.TimePoint
import jade.lang.acl.ACLMessage
import reasoner.common.Tick
import reasoner.{UnknownResult, Reasoner, Result, _}

import scala.collection.immutable.HashMap

/**
  * Created by FM, HB on Feb/Mar 2017.
  *
  * (This class coordinates pinning (within IncrementalRuleMaker) and (then) grounding (IncrementalGrounder))
  */
case class IncrementalReasonerAgent(incrementalRuleMaker: IncrementalRuleMaker, agentReasoner: IReasonerAgent) extends Reasoner {

  println("\n ---- START ----- \n")
  incrementalRuleMaker.staticGroundRules.filter(_.isFact).foreach(a => agentReasoner.sendMessageToTMS(ACLMessage.INFORM, "add",a))
  incrementalRuleMaker.staticGroundRules.filter(r => !r.isFact).foreach(a => agentReasoner.sendMessageToTMS(ACLMessage.INFORM, "add",a))
  println("\n -------------------")

  //time of the truth maintenance network due to previous append and result calls
  var currentTick = Tick(0,0) //using (-1,0), first "+" will fail!

  incrementTick() //...therefore, surpass the increment and generate groundings for (0,0)

  override def append(timepoint: TimePoint)(atoms: Atom*) {
    if (timepoint.value < currentTick.time) {
      throw new RuntimeException(f"cannot append signal at past time t=$timepoint. system time already at t'={currentTick.time}")
    }
    updateToTimePoint(timepoint)
    addSignals(atoms)
  }

  var n=0

  override def evaluate(timepoint: TimePoint): Result = {
    n = n + 1
    //println("evaluate  - incrementalReasonerAgent")
    if (timepoint.value < currentTick.time) {
      return new UnknownResult(f"cannot evaluate past time t=$timepoint. system time already at t'=${currentTick.time}")
    }
    updateToTimePoint(timepoint)
    agentReasoner.sendMessageToTMS(ACLMessage.REQUEST,"getModel", null)
    print(" ", n)
    Result(Option.empty)//jtms.getModel())
  }

  //
  //
  //

  def updateToTimePoint(timepoint: TimePoint) {
    if (timepoint.value > currentTick.time) {
      for (t <- (currentTick.time + 1) to (timepoint.value)) {
        singleTimeIncrementTo(t)
      }
    }
  }

  def singleTimeIncrementTo(time: Long) {
    currentTick = currentTick.incrementTime()
    incrementTick()
  }

  def addSignals(atoms: Seq[Atom]): Unit = {
    atoms.foreach(addSignalAtCurrentTime(_))
  }

  def addSignalAtCurrentTime(signal: Atom) {
    currentTick = currentTick.incrementCount()
    incrementTick(Some(signal))
  }

  def incrementTick(signal: Option[Atom] = None) {

    val timeIncrease = signal.isEmpty

    val expiredRules = if (timeIncrease) {
      expiration.expiringRulesAtTimeIncrement()
    } else {
      expiration.expiringRulesAtCountIncrement()
    }

    removeExpired(expiredRules)

    val annotatedRules: Seq[ExpiringRule] = incrementalRuleMaker.incrementalRules(currentTick, signal)

    processIncrementalRules(annotatedRules)
  }

  var printed=0

  def removeExpired(expiredRules: Seq[NormalRule]): Unit = {
    expiredRules.foreach(removeExpired(_))
  }

  def removeExpired(rule: NormalRule): Unit = {
    agentReasoner.sendMessageToTMS(ACLMessage.INFORM, "remove", rule)
    //println("\n remove expired rules", rule)
    //jtms.remove(rule)
  }

  def processIncrementalRules(expiringRules: Seq[ExpiringRule]): Unit = {
    //println("\nprocessIncrementalRules")

    expiringRules foreach { annotatedRule =>
      addIncrementalRule(annotatedRule.rule)
      registerExpiration(annotatedRule)
    }
  }

  def addIncrementalRule(rule: NormalRule): Unit = {
    agentReasoner.sendMessageToTMS(ACLMessage.INFORM, "add", rule)

    //jtms.add(rule)
  }

  def registerExpiration(annotatedRule: ExpiringRule): Unit = {
    expiration.register(annotatedRule)
  }

  object expiration {

    def register(annotatedRule: AnnotatedNormalRule): Unit = {
      annotatedRule match {
        case RuleExpiringByTimeOnly(rule, exp, mode) => registerByTimeDisj(rule, exp.time)
        case RuleExpiringByCountOnly(rule, exp, mode) => registerByCountDisj(rule, exp.count)
        case RuleExpiringByTimeOrCount(rule, exp, mode) => registerDisjunctive(rule, exp)
        case RuleExpiringByTimeAndCount(rule, exp, mode) => registerConjunctive(rule, exp)
        case _ =>
      }
    }

    def expiringRulesAtTimeIncrement(): Seq[NormalRule] = {
      val disj: Seq[NormalRule] = if (!rulesExpiringAtTimeDisj.contains(currentTick.time)) {
        Seq()
      } else {
        val tmp = rulesExpiringAtTimeDisj.get(currentTick.time).get
        rulesExpiringAtTimeDisj = rulesExpiringAtTimeDisj - currentTick.time
        tmp.toSeq
      }

      if (!incrementalRuleMaker.needConjunctiveAnnotations) {
        return disj
      }

      if (!rulesExpiringAtTimeConj.contains(currentTick.time)) {
        return disj
      }

      val conjCandidates: Set[NormalRule] = rulesExpiringAtTimeConj.get(currentTick.time).get
      val toExpireNow_vs_toExpireLater: (Set[NormalRule], Set[NormalRule]) = conjCandidates.partition(rule => conjunctiveExpirationCandidates.contains(rule))
      rulesExpiringAtTimeConj = rulesExpiringAtTimeConj - currentTick.time
      conjunctiveExpirationCandidates = conjunctiveExpirationCandidates -- toExpireNow_vs_toExpireLater._1
      conjunctiveExpirationCandidates = conjunctiveExpirationCandidates ++ toExpireNow_vs_toExpireLater._2

      return disj ++ toExpireNow_vs_toExpireLater._1
    }

    def expiringRulesAtCountIncrement(): Seq[NormalRule] = {
      val disj: Seq[NormalRule] = if (!rulesExpiringAtCountDisj.contains(currentTick.count)) {
        Seq()
      } else {
        val tmp = rulesExpiringAtCountDisj.get(currentTick.count).get
        rulesExpiringAtCountDisj = rulesExpiringAtCountDisj - currentTick.count
        if (incrementalRuleMaker.hasTupleAtCombination) {
          filterTupleAt(tmp)
        } else { //standard
          tmp.toSeq
        }
      }

      if (!incrementalRuleMaker.needConjunctiveAnnotations) {
        return disj
      }

      if (!rulesExpiringAtCountConj.contains(currentTick.count)) {
        return disj
      }

      val conjCandidates: Set[NormalRule] = rulesExpiringAtCountConj.get(currentTick.count).get
      val toExpireNow_vs_toExpireLater: (Set[NormalRule], Set[NormalRule]) = conjCandidates.partition(rule => conjunctiveExpirationCandidates.contains(rule))
      rulesExpiringAtCountConj = rulesExpiringAtCountConj - currentTick.count
      conjunctiveExpirationCandidates = conjunctiveExpirationCandidates -- toExpireNow_vs_toExpireLater._1
      conjunctiveExpirationCandidates = conjunctiveExpirationCandidates ++ toExpireNow_vs_toExpireLater._2

      return disj ++ toExpireNow_vs_toExpireLater._1
    }


    //see IncrementalTestsLowLevel "tuple at"
    def filterTupleAt(candidateRules: Set[NormalRule]): Seq[NormalRule] = {
      candidateRules.filter { rule =>
        doNotExpireBeforeCount.get(rule) match {
          case Some(needUntilCount) => {
            if (needUntilCount <= currentTick.count) {
              doNotExpireBeforeCount = doNotExpireBeforeCount - rule
              true
            } else {
              false
            }
          }
          case None => true
        }
      }.toSeq
    }
    //

    var rulesExpiringAtTimeDisj: Map[Long, Set[NormalRule]] = HashMap[Long, Set[NormalRule]]()
    var rulesExpiringAtCountDisj: Map[Long, Set[NormalRule]] = HashMap[Long, Set[NormalRule]]()
    //special handling for tuple-box:
    var rulesExpiringAtTimeConj: Map[Long, Set[NormalRule]] = HashMap[Long, Set[NormalRule]]()
    var rulesExpiringAtCountConj: Map[Long, Set[NormalRule]] = HashMap[Long, Set[NormalRule]]()
    var conjunctiveExpirationCandidates = Set[NormalRule]()
    var doNotExpireBeforeCount: Map[NormalRule,Long] = HashMap[NormalRule,Long]()

    private def registerByTimeDisj(rule: NormalRule, time: Long): Unit = {
      rulesExpiringAtTimeDisj = rulesExpiringAtTimeDisj.updated(time, rulesExpiringAtTimeDisj.getOrElse(time, Set()) + rule)
    }

    private def registerByCountDisj(rule: NormalRule, count: Long): Unit = {
      rulesExpiringAtCountDisj = rulesExpiringAtCountDisj.updated(count, rulesExpiringAtCountDisj.getOrElse(count, Set()) + rule)

      //see IncrementalTestsLowLevel "tuple at"
      if (incrementalRuleMaker.hasTupleAtCombination) {
        doNotExpireBeforeCount  = doNotExpireBeforeCount.updated(rule,count)
      }

    }

    private def registerExpirationByTimeConj(rule: NormalRule, time: Long): Unit = {
      rulesExpiringAtTimeConj = rulesExpiringAtTimeConj.updated(time, rulesExpiringAtTimeConj.getOrElse(time, Set()) + rule)
    }

    private def registerExpirationByCountConj(rule: NormalRule, count: Long): Unit = {
      rulesExpiringAtCountConj = rulesExpiringAtCountConj.updated(count, rulesExpiringAtCountConj.getOrElse(count, Set()) + rule)
    }

    private def registerDisjunctive(rule: NormalRule, expiration: Tick): Unit = {
      val t = expiration.time
      val c = expiration.count
      if (t != Void) {
        registerByTimeDisj(rule,t)
      }
      if (c != Void) {
        registerByCountDisj(rule,c)
      }
    }

    private def registerConjunctive(rule: NormalRule, expiration: Tick): Unit = {
      registerExpirationByTimeConj(rule,expiration.time)
      registerExpirationByCountConj(rule,expiration.count)
    }

  } //end object expiration

}
