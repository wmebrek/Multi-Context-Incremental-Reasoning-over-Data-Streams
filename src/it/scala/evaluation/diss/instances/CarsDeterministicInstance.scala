package evaluation.diss.instances

import core.Atom
import evaluation.diss.instances.traits.Instance
import evaluation.diss.programs.CarsProgramProvider
import reasoner.Result
import evaluation.diss.Helpers.{mustHave,mustNotHave,string2Atom}

/**
  * Created by hb on 01.05.18.
  *
  * scale: total number of cars
  * k: threshold - write 'moreThanK' if more than k recorded in timeWindowSize
  *
  */
case class CarsDeterministicInstance(scale: Int, timeWindowSize: Int, k: Int) extends Instance with CarsProgramProvider {

  assert(scale > 0)
  assert(timeWindowSize >= 0)
  assert(k >= 1)

  def rec(i: Int): Atom = f"rec($i)"

  //every time point add a car recording
  def generateSignalsToAddAt(t: Int): Seq[Atom] = {
    val i = {
      if (t == 0) 1 else (t % scale) + 1
    }
    //print(f"\nt=$t: rec($i). ")
    Seq(rec(i))
  }

  override def verifyOutput(result: Result, t: Int): Unit = {
    val model = result.model
    //print(f"model: $model")
    if ((timeWindowSize+1) > k && (t+1) > k) { //-1 since current time point is included, +1 offset from index 0
      mustHave(model,moreThanK,t)
    } else {
      mustNotHave(model,moreThanK,t)
    }
  }

}
