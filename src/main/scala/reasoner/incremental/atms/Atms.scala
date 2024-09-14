package reasoner.incremental.atms

import core.Atom
import core.asp.{NormalProgram, NormalRule}
import m8.atms.nodes.{ANode, Conjunction, Label, Node, NodeType}
import m8.atms.{TMS, TruthRecorder, TruthTeller}

import scala.collection.JavaConverters._


object Atms {

  def apply(P: NormalProgram): Atms = {
    val atms = Atms()
    P.rules foreach atms.add
    atms
  }

  def apply(): Atms = {
    val tms: TMS = new TMS()
    new Atms(tms, new TruthRecorder(tms), new TruthTeller(tms))
  }

}


class Atms(val tms: TMS, val recorder : TruthRecorder, val informant: TruthTeller) extends IAtms {

  override def add(rule: NormalRule): Unit = {
    if(rule.pos.isEmpty && rule.neg.isEmpty){
      // facts
      //println("add fact "+ rule)
      recorder.assume(new Node(rule.head.toString, rule))
    }
    else {
      //println("add rule "+ rule)
      val res = recorder.justify(new Node(rule.head.toString, rule))
      if(rule.pos.nonEmpty) {
        res.withAntecedentsPropagate(rule.pos.map(a => a.toString ).toArray : _*)
      }
      if(rule.neg.nonEmpty ){
        res.withAntecedentsPropagate(rule.neg.map(a => "-"+a.toString).toArray : _*)
      }
      //println("Add justification then propagate => ",rule, res.getJustification)
      tms.propagate(res.getJustification, null, Label.getEmptyEnvironment)
    }

  }

  override def remove(rule: NormalRule): Unit = {

    if(rule.pos.isEmpty && rule.neg.isEmpty) {
      //println("\n\nremove fact", rule )

      tms.removeNode(rule.head.toString)
    }
    else {
      //println("\n\nremove justification and associated nodes", rule )

      tms.getJustificationsMap.get(rule.head.toString).removeIf(j => {
        val conjunction = new Conjunction[ANode]
        rule.pos.foreach(at => conjunction.add(new ANode(at.toString)))
        j.getAntecedents.isProperSupersetOf(conjunction)
      })

      val node = tms.getNode(rule.head.toString)
      if(node.getNodeType.eq(NodeType.Justified)) {
        if(!tms.getJustificationsMap.containsKey(rule.head.toString)){
          tms.removeNode(rule.head.toString)
        }
      }
    }
  }

  override def getModel(): Option[Set[Atom]] = {

    val lnodes : Set[Atom] = tms.getNodes.asScala
      .filter(n => informant.isIn(n.getId))
      .map(n => n.getBaseData.asInstanceOf[NormalRule].head)
      .toSet[Atom]

    if(lnodes.nonEmpty) {
      return Some(lnodes)
    }
    return None
  }

}
