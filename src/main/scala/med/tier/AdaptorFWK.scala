package med.tier

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import common.Events.{NeUnBounded, AdaptorRemoved, NeBounded, NewAdaptor}
import common.Messages.{UnBounded, RemoveAdaptor, Bounded, Announce}
import med.tier.AdaptorFWK._

object AdaptorFWK {
  val ActorName  = "adaptor-fwk"
  val TopicName  = "mediation-events-topic"
}

class AdaptorFWK extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  /** key=neType, value=AdaptorActorRef */
  var adaptors = Map.empty[String, ActorRef]

  import DistributedPubSubMediator.Publish
  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator

  override def receive: Receive = {
    case Announce(neType) =>
      log.info(s"Receive New Adaptor $sender , neType=$neType")
      val adaptor = sender()
      adaptors += (neType -> adaptor)
      // inform Cluster About New Adaptor Instance
      mediator ! Publish(TopicName, NewAdaptor(neType, adaptor))

    case Bounded(neId, neType) =>
      // inform Cluster About New Adaptor Binding
      mediator ! Publish(TopicName, NeBounded(neId, neType, sender()))

    case UnBounded(neId) =>
      // inform Cluster About New Adaptor Binding
      mediator ! Publish(TopicName, NeUnBounded(neId, sender()))

    case RemoveAdaptor(neType) =>
      val adaptor = adaptors(neType)
      adaptor ! PoisonPill
      adaptors -= neType
      mediator ! Publish(TopicName, AdaptorRemoved(neType, adaptor))
  }

}

