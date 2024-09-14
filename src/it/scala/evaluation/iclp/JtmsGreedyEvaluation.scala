package evaluation.iclp

import core.Evaluation
import core.asp.NormalProgram
import reasoner.incremental.jtms.algorithms.JtmsGreedy

/**
  * Created by hb on 25.03.16.
  */
class JtmsGreedyEvaluation extends Evaluation {

  def apply(program: NormalProgram) = {
    val tmn = JtmsGreedy(program)

    val singleModel = tmn.getModel.get

    Set(singleModel)
  }

}
