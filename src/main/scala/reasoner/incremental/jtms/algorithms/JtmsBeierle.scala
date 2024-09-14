package reasoner.incremental.jtms.algorithms

import java.util

import core._
import core.asp.{AspRuleFromBacktracking, NormalProgram, NormalRule}
import reasoner.incremental.jtms._
import reasoner.incremental.jtms.networks.TruthMaintenanceNetwork

import scala.util.Random

object JtmsBeierle {

  def apply(P: NormalProgram): JtmsBeierle = {
    val tmn = new JtmsBeierle()
    P.rules foreach tmn.add
    tmn
  }

}

/**
  * justification-based truth maintenance network
  *
  * follows quite closely the presentation in the
  * book chapter from Beierle and Kern-Isberner
  * (including its bugs ;-) )
  *
  * Created by hb on 12/22/15; 03/25/16
  */
class JtmsBeierle(override val network: TruthMaintenanceNetwork = TruthMaintenanceNetwork(), override val random: Random = new Random()) extends Jtms(network, random) {

  var shuffle = true //debugging

  override def getModel(): Option[scala.collection.immutable.Set[Atom]] = {
    val atoms = network.inAtoms
    if (atoms exists network.contradictionAtom) return None
    Some(atoms.toSet)
  }

  override def add(rule: NormalRule): Unit = {
    updateSteps1to5(rule)
    //6
    for (n <- network.allAtoms) {
      if (network.contradictionAtom(n) && network.status(n) == in) {
        DDBBeierleOriginal(n) //there is no need to continue iteration after first unsolvable contradiction [!]
      }
    }
    //7 would just concern returning the diff (omitted here)
  }

  def updateSteps1to5(rule: NormalRule): Unit = {
    //1
    network.register(rule)
    if (network.status(rule.head) == in) return
    //if (invalid(rule)) { supp(rule.head) += findSpoiler(rule).get; return }
    if (network.invalid(rule)) {
      network.addSupport(rule.head, findSpoiler(rule).get);
      return
    }
    //2
    if (step2(rule)) return
    //3 (first part)
    val L = (network.repercussions(rule.head) + rule.head).toSet

    update(L)
  }

  def step2(rule: NormalRule): Boolean = {
    if (ACons(rule.head).isEmpty) {
      setIn(rule)
      return true
    }
    false
  }

  //extracted at this position for remove case
  override def update(L: Predef.Set[Atom]) {
    updateImpl(L)
  }

  def updateImpl(L: Predef.Set[Atom]): Unit = {

    if (recordChoiceSeq) choiceSeq = Seq[Atom]()
    if (recordStatusSeq) statusSeq = Seq[(Atom, Status, String)]()

    //3 (second part)
    for (atom <- L) {
      step3(atom)
    }
    //4 determine status
    for (atom <- L) {
      step4a(atom)
    }
    //5 fix (choose) status
    step5(L)
  }

  def step3(atom: Atom): Unit = {
    //status(atom) = unknown //vs setUnknown [!]
    //status = status.updated(atom,unknown)
    network.updateStatus(atom, unknown)
  }

  //determine status
  def step4a(atom: Atom): Unit = {
    if (network.status(atom) != unknown)
      return

    network.justifications(atom) find foundedValid match {
      case Some(rule) => {
        setIn(rule)
        for (u <- network.unknownCons(atom)) {
          step4a(u)
        }
      }
      case None => {
        if (network.justifications(atom) forall foundedInvalid) {
          setOut(atom)
          for (u <- network.unknownCons(atom)) {
            step4a(u)
          }
        }
      }
    }
  }

  def step5(L: Predef.Set[Atom]) {

    var atoms = Seq[Atom]() ++ L

    if (doForceChoiceOrder) {
      atoms = (Seq[Atom]() ++ L) sortWith byForcedChoiceSeq
    } else if (shuffle) {
      atoms = shuffleSeq(atoms)
      //println(atoms)
    }

    for (atom <- atoms) {
      step5a(atom)
    }

  }

  def shuffleSeq(atoms: Seq[Atom]): Seq[Atom] = {
    val list = new util.ArrayList[Atom]()
    atoms foreach list.add
    java.util.Collections.shuffle(list)
    var seq = Seq[Atom]()
    for (i <- 0 to list.size() - 1) {
      seq = seq :+ list.get(i)
    }
    seq
  }

  def byForcedChoiceSeq(a: Atom, b: Atom): Boolean = {
    val aIdx = forcedChoiceSeq.indexOf(a)
    val bIdx = forcedChoiceSeq.indexOf(b)
    if (aIdx == -1) return false
    if (bIdx == -1) return true
    aIdx <= bIdx
  }

  //fix (choose) status
  def step5a(atom: Atom): Unit = {
    if (network.status(atom) != unknown)
      return

    network.justifications(atom) find unfoundedValid match {
      case Some(rule) => {
        if (!ACons(atom).isEmpty) {
          for (n <- ACons(atom) + atom) {
            //status(n) = unknown //vs setUnknown [!]
            //status = status.updated(n,unknown)
            network.updateStatus(n, unknown)
            step5a(n) //vs first setting all unknown, and only then call 5a if still necessary [!] (see * below)
          }
        } else {
          setIn(rule)
          for (n <- rule.neg) {
            if (network.status(n) == unknown) {
              //status(n) = out //vs setOutOriginal [!]; support never set!
              //status = status.updated(n,out)
              network.updateStatus(n, out)
            }
          }
          for (u <- network.unknownCons(atom)) {
            //* here other variant is chosen. deliberately? [1]
            step5a(u)
          }
        }
      }
      case None => {
        //all justifications(atom) are unfounded invalid
        //status(atom) = out
        //status = status.updated(atom,out)
        network.updateStatus(atom, out)
        for (rule <- network.justifications(atom)) {
          val n: Option[Atom] = rule.pos find (network.status(_) == unknown) //in general, rule.pos might be empty! [!]
          if (n.isEmpty) {
            throw new RuntimeException("did not find rule.pos atom with status unknown in rule " + rule + " for atom " + atom)
          }
        }
        setOut(atom)
        for (u <- network.unknownCons(atom)) {
          step5a(u)
        }
      }
    }
  }

  //note: there is no such sub-procedure in the book!
  override def setOut(atom: Atom): Unit = {

    if (recordStatusSeq) statusSeq = statusSeq :+ (atom, out, "set")

    //status = status.updated(atom,out)
    network.updateStatus(atom, out)
    setOutSupport(atom)

    //    status(atom) = out
    //    setOutSupport(atom)

    //SuppRule(a) = None //is not set in beierle [!]

  }

  def DDBBeierleOriginal(n: Atom): Unit = {
    if (network.status(n) != in) return

    //1
    val asms = network.foundations(n) filter isAssumption
    val maxAssumptions = asms filter { a =>
      !((asms - a) exists (b => network.foundations(b) contains a))
    }
    if (maxAssumptions.isEmpty)
      return //contradiction cannot be solved

    //2
    val na = maxAssumptions.head //culprit
    val nStar = network.suppRule(na).get.neg.head //(all .neg have status out at this point)

    //3
    val suppRules = maxAssumptions map (network.suppRule(_).get) //J_\bot
    val pos = suppRules flatMap (_.pos) //I_\bot
    val neg = (suppRules flatMap (_.neg)) - nStar //O_\bot
    val rule = AspRuleFromBacktracking(nStar, pos, neg)

    //4
    updateSteps1to5(rule)

    //5
    if (network.status(n) == in) {
      DDBBeierleOriginal(n) //loop? [1]
    }

  }

  def register(a: Atom): Unit = {
    network.register(a)
    if (!network.suppRule.isDefinedAt(a)) {
      network.suppRule = network.suppRule.updated(a, None)
      //suppRule(a) = None
    }
  }

  //ACons(a) = {x ∈ Cons(a) | a ∈ Supp(x)}
  def ACons(a: Atom): Set[Atom] = network.cons(a) filter (network.supp(_) contains a)

  def isAssumption(a: Atom) = (network.status(a) == in) && !network.suppRule(a).get.neg.isEmpty

  def foundedValid(rule: NormalRule) = network.valid(rule)

  def foundedInvalid(rule: NormalRule) = network.invalid(rule)

  def unfoundedValid(rule: NormalRule) = network.posValid(rule)


  def deregister(a: Atom): Unit = {
    network.deregister(a)
    //suppRule remove a
    network.suppRule = network.suppRule - a
  }

  override def setInSupport(a: Atom) = network.justifications(a) find foundedValid match {
    case Some(rule) => {
      network.setInSupport(a, rule)
      //      reasoner.incremental.jtms.suppRule = reasoner.incremental.jtms.suppRule.updated(a,Some(rule))
      //      supp(a) = Set() ++ rule.body
      //      suppRule(a) = Some(rule)
    }
    case _ => throw new IncrementalUpdateFailureException()
  }

  override def setOutSupport(a: Atom) {
    super.setOutSupport(a)
    //suppRule(a) = None //not set in beierle, but relevant only for backtracking
    network.suppRule = network.suppRule.updated(a, None)
  }

  // -- from refactored implementation, not in use --

  //return true if method leaves with status(c) != in
  def DDBRefactored(c: Atom): Boolean = {

    if (network.status(c) != in) return true

    val asms = network.foundations(c) filter isAssumption
    val maxAssumptions = asms filter { a =>
      !((asms - a) exists (b => network.foundations(b) contains a))
    }

    if (maxAssumptions.isEmpty)
      return false //contradiction cannot be solved

    findBacktrackingRule(maxAssumptions) match {
      case Some(rule) => add(rule); return true
      case None => return false
    }

  }

  def findBacktrackingRule(maxAssumptions: Set[Atom]): Option[AspRuleFromBacktracking] = {

    val culprit = maxAssumptions.head
    val n = network.suppRule(culprit).get.neg.head //(all .neg have status out at this point)

    val suppRules = maxAssumptions map (network.suppRule(_).get)
    val pos = suppRules flatMap (_.pos)
    val neg = (suppRules flatMap (_.neg)) - n
    val rule = AspRuleFromBacktracking(n, pos, neg)

    Some(rule)
  }

}