package reasoner.parser.factories

import core.lars.ExtendedAtom

/**
  * Created by et on 22.03.17.
  */
trait AtomTrait extends BodyTrait {

  val neg: Boolean = false
  val atom: ExtendedAtom
}
