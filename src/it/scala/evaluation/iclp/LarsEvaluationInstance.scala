package evaluation.iclp

import core.asp.{NormalRule, UserDefinedAspFact, UserDefinedAspRule}
import core.lars._
import core.{Atom, Model, PinnedAtom}

import scala.util.Random

/**
  * Created by hb on 17.04.17.
  */
trait LarsEvaluationInstance {

  val timePoints: Int
  def larsProgram(windowSize: Int): LarsProgram
  def verifyModel(model: Option[Model], t: Int)
  def generateSignalsToAddAt(t: Int): Seq[Atom]
  def random: Random

  //
  //
  //

  def wAt(windowSize: Int, time: Time, atom: Atom) = WindowAtom(TimeWindow(windowSize), At(time), atom)
  def wD(windowSize: Int, atom: Atom) = WindowAtom(TimeWindow(windowSize), Diamond, atom)
  def wB(windowSize: Int, atom: Atom) = WindowAtom(TimeWindow(windowSize), Box, atom)
  def tup_wAt(windowSize: Int, time: Time, atom: Atom) = WindowAtom(TupleWindow(windowSize), At(time), atom)
  def tup_wD(windowSize: Int, atom: Atom) = WindowAtom(TupleWindow(windowSize), Diamond, atom)
  def tup_wB(windowSize: Int, atom: Atom) = WindowAtom(TupleWindow(windowSize), Box, atom)

  //

  def contains(model: Model, t: Int, a: Atom) = {
    if (!model.contains(a)) {
      //printModel(t,model)
      println(f"model at t=$t does not contain $a")
      assert(false)
    }
    true
  }
  def notContains(model: Model, t: Int, a: Atom) = {
    if (model.contains(a)) {
      //printModel(t,model)
      println(f"model at t=$t contains $a")
      assert(false)
    }
    true
  }
  def containsSomeOf(model: Model, t: Int, ats: Seq[Atom]) = {
    if (!(ats.exists(model.contains(_)))) {
      //printModel(t,model)
      println(f"model at t=$t contained none of $ats")
      assert(false)
    }
    true
  }

  def printModel(t:Int, model: Set[Atom]): Unit = {
    println(f"\nt=$t")
    model foreach println
  }



  //
  //helper methods
  //

  def rule(head: Atom, posBody: Set[Atom], negBody: Set[Atom]): NormalRule = {
    UserDefinedAspRule[Atom](head, posBody, negBody)
  }

  def rule(head: Atom, posBody: Atom): NormalRule = {
    UserDefinedAspRule[Atom](head, Set(posBody), Set())
  }

  def rule(head: Atom, posBody: Atom, negBody: Atom): NormalRule = {
    UserDefinedAspRule[Atom](head, Set(posBody), Set(negBody))
  }

  def fact(head: Atom): NormalRule = UserDefinedAspFact[Atom](head)

  def larsFact(head: Atom): LarsRule = UserDefinedLarsRule(head,Set(),Set())

  def pinnedFact(a: Atom, t: Int): NormalRule = fact(PinnedAtom.asPinnedAtAtom(a,TimePoint(t)))
}
