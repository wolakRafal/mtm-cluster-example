import akka.actor.ActorRef

object Events {

  sealed trait Event{def eventId: Long}

  sealed trait NCEvent

  /** Event published by AdaptorFWK, recipients are all MTMs in cluster */
  final case class NewAdaptor(neType: String, adaptorAddress: ActorRef) extends NCEvent

  // Event published by Adaptor/AdaptorFWK when bind operation succeed, recipients are all MTMs in cluster
//  final case class AdaptorBound(neType: String) extends EventInternal

}

