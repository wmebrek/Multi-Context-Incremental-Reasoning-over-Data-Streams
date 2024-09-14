package agents.sensing

import jade.core.AID

trait ISensingAgent {
  def addAgentToDest(agent:AID): Unit
}
