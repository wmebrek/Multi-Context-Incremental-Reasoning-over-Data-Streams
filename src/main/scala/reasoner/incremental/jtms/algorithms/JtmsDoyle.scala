package reasoner.incremental.jtms.algorithms

import core._
import core.asp.{NormalProgram, NormalRule}
import reasoner.incremental.jtms._
import reasoner.incremental.jtms.networks.TruthMaintenanceNetwork

import scala.util.Random

object JtmsDoyle {

  def apply(P: NormalProgram): JtmsDoyle = {
    val jtms = new JtmsDoyle()
    P.rules foreach jtms.add
    jtms
  }

  def apply(): JtmsDoyle = new JtmsDoyle()

}

/**
  * justification-based truth maintenance network
  *
  * specific algorithm obtained as refactoring from
  * pseudo-code in book chapter from Beierle and Kern-Isberner
  * (who in turn simplify Doyle's original algorithm)
  *
  * Created by hb on 12/22/15.
  */
class JtmsDoyle(override val network: TruthMaintenanceNetwork = TruthMaintenanceNetwork(), override val random: Random = new Random()) extends Jtms(network, random) {

  var doSelfSupportCheck = false
  var doConsistencyCheck = false //detect wrong computation of odd loop, report inconsistency

  //for inspection:
  var doJtmsSemanticsCheck = false //for debugging

  var failed = false

  override def update(atoms: Set[Atom]): Unit = {
    println("update call")
    if (recordChoiceSeq) choiceSeq = Seq[Atom]()
    if (recordStatusSeq) statusSeq = Seq[(Atom, Status, String)]()

    try {
      updateImplementation(atoms)

      checkJtmsSemantics()
      checkSelfSupport()
      checkConsistency()
    } catch {
      case e: IncrementalUpdateFailureException => {
        invalidateModel()
      }
    }

  }

  def updateImplementation(atoms: Set[Atom]): Unit = {
    println("update implementation => unknown + Findstatus")
    atoms foreach setUnknown //Marking the nodes
    atoms foreach findStatus // Evaluating the nodes' justifications

    if (doForceChoiceOrder) {
      // Relaxing circularities (might lead to contradictions)
      val atomList = sortByForcedOrder(atoms)
      atomList foreach chooseStatus
    } else {
      atoms foreach chooseStatus
    }
    //omit backtracking part
  }

  def chooseStatus(a: Atom): Unit = {
    println("choose status")
    if (!isUnknown(a)) return

    if (choice(a)) {
      if (recordChoiceSeq) choiceSeq = choiceSeq :+ a
      chooseRecursive(network.unknownCons(a))
      //network.unknownCons(a) foreach findStatus //variant
    } else {
      retractionsAffected = retractionsAffected + 1
      retractChoice(a)
    }
  }

  def chooseRecursive(atoms: Set[Atom]): Unit = {
    println("choose recursive")
    atoms foreach chooseStatus
  }

  def retractChoice(a: Atom): Unit = {
    println("retract choice")
    val aff = shuffle(network.affected(a) + a)
    //val aff = affected(a) + a
    aff foreach setUnknown
    aff foreach chooseStatus
  }

  var retractionsAffected = 0

  def choice(a: Atom): Boolean = {
    println("choice")
    network.justifications(a) find network.posValid match {
      case Some(rule) => {
        if (network.affected(a).isEmpty) setIn(rule)
        else return false
      }
      case None => setOut(a) //allowing 'unknown' instead of 'out' in spoiler!
    }
    true
  }

  /* allows also an unknown atom instead of an out atom, but only if necessary.
     this way, no further heuristic (i.e. losses of potential solutions) are introduced.
     in particular, the method can be used both in the deterministic step
     (which seeks a spoiler only for a (well-founded) invalid rule, i.e., no unknown atom occurs.)
     as well as in the choice step, where an unknown atom might indeed be needed.
   */
  override def findSpoiler(rule: NormalRule): Option[Atom] = {
    if (random.nextDouble() < 0.5) {
      rule.pos find (network.status(_) == out) match {
        case None => rule.neg find (network.status(_) == in) match {
          case None => rule.pos find (network.status(_) == unknown)
          case opt => opt
        }
        case opt => opt
      }
    } else {
      rule.neg find (network.status(_) == in) match {
        case None => rule.pos find (network.status(_) == out) match {
          case None => rule.pos find (network.status(_) == unknown)
          case opt => opt
        }
        case opt => opt
      }
    }
  }

  def sortByForcedOrder(atoms: Set[Atom]): Seq[Atom] = {
    val atomList = Seq[Atom]() ++ atoms

    def sort(a: Atom, b: Atom): Boolean = {
      if (!choiceSeq.contains(b)) return true
      if (!choiceSeq.contains(a)) return false
      choiceSeq.indexOf(a) <= choiceSeq.indexOf(b)
    }

    atomList sortWith sort
  }

  def shuffle(atoms: Set[Atom]): Seq[Atom] = random.shuffle(atoms.toSeq)

  def checkJtmsSemantics(): Unit = {
    if (!doJtmsSemanticsCheck) return
    if (network.atomsNeedingSupp exists (network.supp(_).isEmpty)) {
      throw new RuntimeException(f"model: ${getModel()} \nno support for atoms ${(network.atomsNeedingSupp filter (network.supp(_).isEmpty))}")
    }
  }

  def checkSelfSupport(): Unit = {
    if (!doSelfSupportCheck) return
    if (network.inAtoms exists unfoundedSelfSupport) {
      //throw new RuntimeException("model: "+getModel()+"\nself support exists")
      Console.err.println(f"model: ${getModel()} \nself support exists")
      failed = true
      invalidateModel()
    }
  }

  def checkConsistency(): Unit = {
    if (!doConsistencyCheck) return
    if ((network.inAtoms diff network.factAtoms) exists (a => !(network.justifications(a) exists network.valid))) {
      //throw new RuntimeException("model: "+getModel()+"\ninconsistent state: in-atom has no valid justification")
      Console.err.println(f"model: ${getModel()} \ninconsistent state: in-atom has no valid justification")
      failed = true
      invalidateModel()
    }
    if ((network.outAtoms diff network.factAtoms) exists (a => (network.justifications(a) exists network.valid))) {
      //throw new RuntimeException("model: "+getModel()+"\ninconsistent state: out-atom has valid justification")
      Console.err.println(f"model: ${getModel()} \ninconsistent state: out-atom has valid justification")
      failed = true
      invalidateModel()
    }
  }

  def selfSupport(a: Atom): Boolean = network.supp(a) contains a

  def unfoundedSelfSupport(a: Atom): Boolean = {
    if (!selfSupport(a)) return false
    network.justifications(a) filter network.valid exists (r => !(r.pos contains a))
  }
}