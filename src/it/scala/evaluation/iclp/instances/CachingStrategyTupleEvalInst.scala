package evaluation.iclp.instances

import core._
import core.asp.NormalRule
import core.lars._
import evaluation.iclp.StreamingTmsEvalInst

import scala.util.Random

/**
  * Created by hb on 04.04.17.
  */
abstract class CachingStrategyTupleEvalInst(windowSize: Int, timePoints: Int, random: Random, val values: Seq[Int] = (0 to 30)) extends StreamingTmsEvalInst {

  val done = Atom("done")
  val lfu = Atom("lfu")
  val lru = Atom("lru")
  val fifo = Atom("fifo")
  val randomAtom = Atom("random")

  val _alpha_at = Predicate("alpha_at")
  val _high_at = Predicate("high_at")
  val _mid_at = Predicate("mid_at")
  val _low_at = Predicate("low_at")
  val _rtm_at = Predicate("rtm50_at")
  val _value = Predicate("value")

  val spoil_high = Atom("spoil_high")
  val spoil_mid = Atom("spoil_mid")
  val spoil_low = Atom("spoil_low")

  val wrtm = Atom("wrtm")


  // LARS:
  val _alpha = Predicate("alpha")
  val high = Atom("high")
  val mid = Atom("mid")
  val low = Atom("low")


  def alpha(arg: Argument) = AtomWithArguments(_alpha, Seq(arg))

  def value(arg: Argument) = AtomWithArguments(_value, Seq(arg))


  override def staticRules: Seq[NormalRule] = ???

  override def immediatelyExpiringRulesFor(t: Int): Seq[NormalRule] = ???

  override def rulesExpiringAfterWindow(t: Int): Seq[NormalRule] = ???

  override def larsProgram(windowSize: Int): LarsProgram = {
    val T: Variable = StringVariable("T")
    val V: Variable = StringVariable("V")

    def s(ats: Atom*): Set[ExtendedAtom] = ats.toSet

    val n = windowSize

    val rules = Seq[LarsRule](
      AtAtom(T, high) <= value(V) and tup_wAt(n, T, _alpha(V)) and Leq(IntValue(18), V),
      AtAtom(T, mid) <= value(V) and tup_wAt(n, T, _alpha(V)) and Leq(IntValue(12), V) and Lt(V, IntValue(18)),
      AtAtom(T, low) <= value(V) and tup_wAt(n, T, _alpha(V)) and Lt(V, IntValue(12)),
      lfu <= wB(n, high),
      lru <= wB(n, mid),
      fifo <= wB(n, low),
      done <= lfu,
      done <= lru,
      done <= fifo,
      UserDefinedLarsRule(randomAtom, s(), s(done))
    ) ++ (factAtoms map larsFact)

    LarsProgram(rules)

  }

  def factAtoms(): Seq[Atom] = {
    values map (IntValue(_)) map (value(_))
  }

}

case class CachingStrategyTupleEvalInstDet(windowSize: Int, timePoints: Int, random: Random, override val values: Seq[Int] = Seq(5, 15, 25)) extends CachingStrategyTupleEvalInst(windowSize, timePoints, random) {

  override def verifyModel(optModel: Option[Model], t: Int) = {

  }

  override def generateSignalsToAddAt(t: Int): Seq[Atom] = {
    Seq[Atom]() :+ alpha(alphaValueFor(t))
  }

  def alphaValueFor(t: Int): IntValue = {
    val q = t % 180
    if (0 <= q && q < 60) IntValue(5)
    else if (q >= 60 && q < 120) IntValue(15)
    else IntValue(25)
  }

}

case class CachingStrategyTupleEvalInstNonDet(windowSize: Int, timePoints: Int, random: Random, override val values: Seq[Int] = (0 to 30)) extends CachingStrategyTupleEvalInst(windowSize, timePoints, random) {

  val simplify = values.size == 3

  abstract class Mode

  case object High extends Mode

  case object Mid extends Mode

  case object Low extends Mode

  case object RandomMode extends Mode

  var mode: Mode = RandomMode
  var modeUntil = 1
  var lastRtm = -10000

  override def generateSignalsToAddAt(t: Int): Seq[Atom] = {
    if (modeUntil == t) {
      mode = random.nextInt(4) match {
        case 0 => High
        case 1 => Mid
        case 2 => Low
        case 3 => RandomMode
      }
      modeUntil = t + 2 * windowSize
    }
    val v = mode match {
      case High => if (simplify) 20 else (18 + random.nextInt(13))
      case Mid => if (simplify) 15 else (12 + random.nextInt(6))
      case _ => if (simplify) 10 else random.nextInt(12)
    }
    var signals = Seq[Atom]() :+ alpha(IntValue(v))
//    if (mode == Low) {
//      if ((t - lastRtm > windowSize) || (random.nextDouble() < (1.0 / (1.0 * windowSize / 2.0)))) {
//        signals = signals :+ rtm50
//        lastRtm = t
//      }
//    }
    //addedFacts = addedFacts + (t -> (signals map (fact(_))))
    //println(f"$t -> $signals")
    signals
  }

  override def verifyModel(optModel: Option[Model], t: Int): Unit = {

  }

}