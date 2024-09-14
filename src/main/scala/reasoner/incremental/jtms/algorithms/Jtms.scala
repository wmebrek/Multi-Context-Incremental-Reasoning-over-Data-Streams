package reasoner.incremental.jtms.algorithms

import core.Atom
import core.asp._
import reasoner.incremental.jtms._
import reasoner.incremental.jtms.networks.{OptimizedNetwork, OptimizedNetworkForLearn, TruthMaintenanceNetwork}

import scala.util.Random

/**
  * Created by FM on 13.10.16., major update HB 7.12.17
  */
object Jtms {

  sealed trait Variant
  object DoyleHeuristics extends Variant
  object Doyle extends Variant
  //old and experimental:
  object Beierle extends Variant
  object BeierleFixed extends Variant
  object Greedy extends Variant
  object Learn extends Variant

  //default implementation
  var variant: Variant = DoyleHeuristics

  def apply(P: NormalProgram): Jtms = {
    val jtms = Jtms()
    P.rules foreach jtms.add
    jtms
  }

  def apply(random: Random = new Random()): Jtms = {
    variant match {
      case DoyleHeuristics => new JtmsDoyleHeuristics(TruthMaintenanceNetwork(), random)
      case Doyle => new JtmsDoyle(TruthMaintenanceNetwork(), random)
      //
      case Beierle => new JtmsBeierle(TruthMaintenanceNetwork(), random)
      case BeierleFixed => new JtmsBeierleFixed(TruthMaintenanceNetwork(), random)
      case Greedy => new JtmsGreedy(TruthMaintenanceNetwork(), random)
      case Learn => new JtmsLearn(new OptimizedNetworkForLearn(), random)
    }
  }

  def apply(network: TruthMaintenanceNetwork, random: Random): Jtms = {
    variant match {
      case DoyleHeuristics => new JtmsDoyleHeuristics(network, random)
      case Doyle => new JtmsDoyle(network, random)
      //
      case Beierle => new JtmsBeierle(network, random)
      case BeierleFixed => new JtmsBeierleFixed(network, random)
      case Greedy => new JtmsGreedy(network, random)
      case Learn => new JtmsLearn(network.asInstanceOf[OptimizedNetworkForLearn], random)
    }
  }
}

abstract class Jtms(val network: TruthMaintenanceNetwork = new OptimizedNetwork(), val random: Random = new Random()) extends MaintenanceAlgorithm with ChoiceControl {

  override def rules: Set[NormalRule] = network.rules

  override def add(rule: NormalRule): Unit = {
    println("add => ", rule)
    register(rule)
    /* inconsistent case not in use anymore
    if (network.inconsistent) {
      update(network.unknownAtoms + rule.head) //i.e., recompute()
    } else {
    */
      if (network.status(rule.head) == in) {
        println("run already in heuristic", rule)
        ruleAlreadyInHeuristic(rule)
        return
      }
      if (network.invalid(rule)) {
        println("invalid rule => add support", rule)
        network.addSupport(rule.head, findSpoiler(rule).get)
        return
      }
      val atoms = network.repercussions(rule.head) + rule.head
      println("repercussion =>", rule.head)
      update(atoms)
    //}
  }

  override def remove(rule: NormalRule): Unit = {
    println("\n\nremove ", rule)
    deregister(rule)
    /* inconsistent case not in use anymore
    if (network.inconsistent) {
      val h = if (network.allAtoms contains rule.head) Set(rule.head) else Set()
      update(network.unknownAtoms ++ h)
    } else {
    */
      if (!(network.allAtoms contains rule.head)) {
        println("network allAtoms contains rule", rule.head);
        println(!(network.allAtoms contains rule.head))
        return
      }
      if (network.status(rule.head) == out) return
      if (network.suppRule(rule.head).isDefined && network.suppRule(rule.head).get != rule) return
      //.isDefined needed if previous state was inconsistent
      val atoms = network.repercussions(rule.head) + rule.head
      println("repercussion remove", rule.head)
      update(atoms)
    //}
  }

  override def getModel(): Option[Set[Atom]] = {
    val atoms = network.inAtoms
    println("getModel IN=> ", atoms)
    println("getModel isOut => ", network.outAtoms)
    println("getModel unknown => ", network.unknownAtoms)
    //if (atoms exists network.contradictionAtom) return None //not dealt with; left for old test-cases
    if (network.hasUnknown) return None
    Some(atoms)
  }

  override def set(model: Set[Atom]): Boolean = {
    invalidateModel()
    println("invalidate model")
    model foreach { atom =>
      println("update status")
      network.updateStatus(atom, in)
    }
    (network.allAtoms diff model) foreach { atom =>
      println("update status v2")
      network.updateStatus(atom, out)
    }
    try {
      network.atomsNeedingSupp foreach setSupport
    } catch {
      case e: IncrementalUpdateFailureException => {
        invalidateModel()
        return false
      }
    }
    true
  }

  def findSpoiler(rule: NormalRule): Option[Atom] = {
    println("find spoiler", rule, random)
    if (random.nextDouble() < 0.5) {
      rule.pos find (network.status(_) == out) match {
        case None => rule.neg find (network.status(_) == in)
        case opt => opt
      }
    } else {
      rule.neg find (network.status(_) == in) match {
        case None => rule.pos find (network.status(_) == out)
        case opt => opt
      }
    }
  }

  def findStatus(a: Atom): Unit = {
    println("find status", a)
    if (!isUnknown(a)) return

    if (validation(a) || invalidation(a)) {
      println("validation / invalidation", a)
      recursiveFindStatus(network.unknownCons(a))
    }
  }

  def isUnknown(a: Atom): Boolean = {
    println("isUnknown", a,     network.status(a) == unknown)
    network.status(a) == unknown
  }

  def recursiveFindStatus(atoms: Set[Atom]): Unit = {
    println("recursiveFindStatus ")
    atoms foreach findStatus
  }

  def validation(a: Atom): Boolean = {
    println("validation ", a)
    network.justifications(a) find network.valid match {
      case Some(rule) => setIn(rule); true
      case None => false
    }
  }

  def invalidation(a: Atom): Boolean = {
    println("invalidation ", a)
    if (network.justifications(a) forall network.invalid) {
      setOut(a)
      return true
    }
    false
  }

  def invalidateModel(): Unit = {
    println("invalidate model")
    network.atomsNeedingSupp foreach setUnknown
  }

  def register(rule: NormalRule): Boolean = {
    println("register ", rule)
    if (network.register(rule)) {
      if (recordStatusSeq) {
        val atoms = network.outAtoms
        val newOutAtoms = network.outAtoms diff atoms
        newOutAtoms foreach { a =>
          statusSeq = statusSeq :+ (a, out, "register")
        }
      }
      return true
    }
    false
  }

  def deregister(rule: NormalRule) = network.deregister(rule)

  def ruleAlreadyInHeuristic(rule: NormalRule): Unit = {} //see JtmsDoyleHeuristics

  def recompute(): Unit = {
    update(network.unknownAtoms)
  }

  def setSupport(a: Atom) {
    println("set support", a)
    network.status(a) match {
      case `in` => setInSupport(a)
      case `out` => setOutSupport(a)
      case `unknown` => network.clearSupport(a)
    }
  }

  def setInSupport(a: Atom) = network.justifications(a) find network.valid match {
    case Some(rule) => {
      network.setInSupport(a, rule)
      assert(a == rule.head)
    }
    case _ => throw new IncrementalUpdateFailureException()
  }

  def setOutSupport(a: Atom) {
    val maybeAtoms: Set[Option[Atom]] = network.justifications(a) map findSpoiler
    println("set out support")
    if (maybeAtoms exists (_.isEmpty)) {
      throw new IncrementalUpdateFailureException("could not find spoiler for every justification of atom " + a)
    }
    network.setOutSupport(a, maybeAtoms map (_.get))
  }

  def setIn(rule: NormalRule) = {
    println("set in")
    if (recordStatusSeq) statusSeq = statusSeq :+ (rule.head, in, "set")
    network.updateStatus(rule.head, in)
    network.setInSupport(rule.head, rule)
  }

  def setOut(a: Atom) = {
    println("set out")
    if (recordStatusSeq) statusSeq = statusSeq :+ (a, out, "set")
    network.updateStatus(a, out)
    setOutSupport(a)
  }

  def setUnknown(a: Atom) = {
    println("set unknown")
    network.updateStatus(a, unknown)
    network.clearSupport(a)
  }

  //book keeping:
  var recordStatusSeq = false
  var statusSeq = Seq[(Atom, Status, String)]()

  var recordChoiceSeq = false
  var choiceSeq = Seq[Atom]()

}
