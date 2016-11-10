package common

import akka.actor.{Address, ActorRef}

object Events {

  sealed trait Event{def eventId: Long}

  sealed trait NCEvent

  /** Events published by AdaptorFWK, recipients are all MTMs in cluster */
  case class NewAdaptor(neType: String, adaptorAddress: ActorRef) extends NCEvent

  case class AdaptorRemoved(neType: String, adaptorAddress: ActorRef) extends NCEvent

  /** Event Published by Adaptor FWK to All app nodes (MTMs) */
  case class NeBounded(neId: String, neType:String, adaptorAddress: ActorRef) extends NCEvent
  case class NeUnBounded(neId: String, adaptorAddress: ActorRef) extends NCEvent

  // Event published by Adaptor/AdaptorFWK when bind operation succeed, recipients are all MTMs in cluster
//  final case class AdaptorBound(neType: String) extends EventInternal

}

