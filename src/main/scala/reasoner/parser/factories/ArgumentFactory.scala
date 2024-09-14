package reasoner.parser.factories

import core.{Argument, IntValue, NumericArgument, StringValue, Variable}
import reasoner.parser.InvalidSyntaxException

/**
  * Created by et on 22.03.17.
  */
case class ArgumentFactory(operand: Any) {

  lazy val arg: Argument = create(operand)

  @throws[InvalidSyntaxException]
  def create(operand: Any): Argument =
    operand match {
    case arg: Double  => createNumeric(arg.##)
    case arg: Int     => createNumeric(arg)
    case arg: String  => {
      if(arg.head.isUpper) {
        createNumeric(arg)
      }
      else StringValue(arg)
    }
    case _            => throw new InvalidSyntaxException("Invalid operand class: "+operand.getClass.toString)
  }

  @throws[InvalidSyntaxException]
  def createNumeric(operand: Any): NumericArgument = operand match {
    case arg: Double  => IntValue(arg.##)
    case arg: Int     => IntValue(arg)
    case arg: String  => Variable(arg)
    case _            => throw new InvalidSyntaxException("Invalid operand class: "+operand.getClass.toString)
  }
}
