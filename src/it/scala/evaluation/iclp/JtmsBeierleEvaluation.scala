package evaluation.iclp

import core.Evaluation
import core.asp.NormalProgram
import reasoner.incremental.jtms.algorithms.JtmsBeierle

/**
  * Created by FM on 25.02.16.
  */
class JtmsBeierleEvaluation extends Evaluation {

  def apply(program: NormalProgram) = {
    val tmn = JtmsBeierle(program)

    val singleModel = tmn.getModel.get

    Set(singleModel)
  }

}
