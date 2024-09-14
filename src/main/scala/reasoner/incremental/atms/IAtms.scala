package reasoner.incremental.atms

import core.Model
import core.asp.NormalRule

/**
  * Abstraction of update mechanism
  *
  * Created by hb on 07.12.17.
  */
trait IAtms {

  def add(rule: NormalRule)

  def remove(rule: NormalRule)

  def getModel(): Option[Model]

}
