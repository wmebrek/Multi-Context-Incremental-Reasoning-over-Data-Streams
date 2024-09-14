package core.grounding

import core.Atom
import core.asp.{AspRule, NormalRule}

/**
  * Incremental ASP grounded tailored for use in Incremental Engine based on LARS mapping.
  *
  * Created by hb on 08.03.17.
  */
case class Pregrounder() {

  var allRules: List[NormalRule] = null
  var inspection: StaticProgramInspection[NormalRule, Atom, Atom] = null
  var grounder: RuleGrounder[NormalRule, Atom, Atom] = null

  var rulesUpdated=true

  def init(rules: Seq[NormalRule]): Unit = {
    allRules = rules.toList
    inspection = StaticProgramInspection[NormalRule, Atom, Atom](allRules)
    grounder = GrounderInstance.forAsp(inspection)
    rulesUpdated=true
  }

  def groundPartially(rule: NormalRule, signal: Option[Atom] = None): Set[NormalRule] = {
    grounderCall(rule,false, signal)
  }

  def groundFully(rule: NormalRule): Set[NormalRule] = {
    grounderCall(rule,true)
  }

  private def grounderCall(rule: NormalRule, ensureGroundResult: Boolean, signal: Option[Atom] = None): Set[NormalRule] = {
    if (rulesUpdated) {
      inspection = StaticProgramInspection[NormalRule, Atom, Atom](allRules)
      grounder = GrounderInstance.forAsp(inspection)
      rulesUpdated = false
    }
    if(signal.nonEmpty){
      val newRuleTmp = AspRule[Atom](signal.get)
      val updatedInspection = inspection.addRule(newRuleTmp)
      grounder = GrounderInstance.forAsp(updatedInspection)
    }
    grounder.ground(rule, ensureGroundResult)
  }

}
