package common

import akka.actor.ActorRef

object Messages {

  final case class GetAdaptorAddress(meSelfUri: String)

  final case class AdaptorAddress(meSelfUri: String, adaptor: ActorRef)

  final case class NoAdaptors(meSelf: String, reason: String)

  case object GetAllAdaptors

  final case class AllAdaptors(adaptors: Map[String, List[ActorRef]])

  /** Adaptor uses this message to announce himself to adaptor framework **/
  final case class Announce(neType: String)

  final case class GlobalRoutingState(adaptors: Map[String, List[ActorRef]], routingTable: Map[String, ActorRef])

}
