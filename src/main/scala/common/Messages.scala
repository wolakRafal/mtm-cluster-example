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
  /** Adaptor uses this message to inform adaptor framework that he has bound to that ne **/
  final case class Bounded(neId: String, neType: String)
  final case class UnBounded(neId: String)

  final case class GlobalRoutingState(adaptors: Map[String, List[ActorRef]], routingTable: Map[String, ActorRef])

  final case class BindNe(neId: String, topicName: String)
  final case class UnbindNe(neId: String)


  final case class CreateMe(neId: String, neType: String)
  final case class DeleteMe(neId: String)

  /** Administrative operation - tells adaptor framework to remove(unload) Adaptor bundle/instance from the system */
  final case class RemoveAdaptor(neType: String)
}
