package core.asp

import core.{Atom, Fact, Predicate, Rule}

sealed trait AspRule[TAtom <: Atom] extends Rule[TAtom, TAtom] with Serializable{

  override lazy val atoms: Set[TAtom] = body + head

  override def toString = {
    val sb = new StringBuilder
    if(head.equals("xx")){
      sb.append("⊥")
    } else {
      sb.append(head)
    }
    if (pos.nonEmpty || neg.nonEmpty) {
      sb.append(" :- ")
    }
    if (!pos.isEmpty) {
      sb.append(pos.head)
      if (pos.size > 1) {
        pos.tail foreach (b => sb.append(", ").append(b))
      }
    }
    if (!neg.isEmpty) {
      if (!pos.isEmpty) {
        sb.append(", ")
      }
      sb.append("not ").append(neg.head)
      if (neg.size > 1) {
        neg.tail foreach (b => sb.append(", not ").append(b))
      }
    }
    sb.append(".")
    sb.toString
  }

}

trait AspFact[TAtom <: Atom] extends AspRule[TAtom] with Fact[TAtom, TAtom] with Serializable

object AspRule extends Serializable{

  def pos[TAtom <: Atom](atoms: TAtom*) = new RuleBuilder(atoms.toSet)

  def neg[TAtom <: Atom](atoms: TAtom*) = new RuleBuilder(Set(), atoms.toSet)

  def pos(predicate: Predicate) = new RuleBuilder(Set(Atom(predicate)))

  def neg(predicate: Predicate) = new RuleBuilder(Set(), Set(Atom(predicate)))


  def fact[TAtom <: Atom](head: TAtom) = AspFact(head)

  def apply[TAtom <: Atom](head: TAtom, pos: Set[TAtom], neg: Set[TAtom]) = UserDefinedAspRule(head, pos, neg)

  def apply[TAtom <: Atom](head: TAtom): AspFact[TAtom] = AspFact(head)

  def apply[TAtom <: Atom](head: TAtom, pos: TAtom) = UserDefinedAspRule(head, Set(pos), Set())

  def apply[TAtom <: Atom, TPos <: TAtom](head: TAtom, pos: Set[TPos]) = UserDefinedAspRule(head, pos.toSet[TAtom], Set[TAtom]())

}

case class UserDefinedAspRule[TAtom <: Atom](head: TAtom, pos: Set[TAtom], neg: Set[TAtom]) extends AspRule[TAtom] with Serializable{

  //def this() = this(head, pos, neg)

  override def from(h: TAtom, p: Set[TAtom], n: Set[TAtom]): UserDefinedAspRule[TAtom] = {
    UserDefinedAspRule(h, p, n)
  }

  private lazy val precomputedHash = scala.runtime.ScalaRunTime._hashCode(UserDefinedAspRule.this)

  override def hashCode(): Int = precomputedHash

  //    override lazy val hashCode(): Int = scala.runtime.ScalaRunTime._hashCode(UserDefinedAspRule.this)

  //  override def equals(other: Any): Boolean = other match {
  //    case a: UserDefinedAspRule[TAtom] => this.precomputedHash == a.precomputedHash
  //    case _ => super.equals(other)
  //  }
}

case class AspRuleFromBacktracking(head: Atom, pos: Set[Atom], neg: Set[Atom]) extends NormalRule {
  override def from(h: Atom, p: Set[Atom], n: Set[Atom]) = {
    AspRuleFromBacktracking(h, p, n)
  }

  override def toString = {
    super.toString.replaceAll("<-", "<--")
  }
}

//object AspRuleFromBacktracking {
//  def apply(head: Atom, pos: Set[Atom], neg: Set[Atom]): AspRuleFromBacktracking = {
//    AspRuleFromBacktracking(head, pos.toSet, neg.toSet)
//  }
//}

object AspFact {
  def apply[TAtom <: Atom](fact: TAtom): AspFact[TAtom] = UserDefinedAspFact(fact)

  def apply(predicate: Predicate): AspFact[Atom] = UserDefinedAspFact(Atom(predicate))
}

case class UserDefinedAspFact[TAtom <: Atom](head: TAtom) extends AspFact[TAtom] {
  override def from(h: TAtom, p: Set[TAtom], n: Set[TAtom]): UserDefinedAspFact[TAtom] = {
    UserDefinedAspFact(h)
  }

  //  override def toString = {
  //    head.toString
  //  }
}