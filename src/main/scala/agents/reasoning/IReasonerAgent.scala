package agents.reasoning

trait IReasonerAgent {
  def sendMessageToTMS(verb: Int, typeMsg:String, msg: Serializable): Unit
}
