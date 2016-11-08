package med.tier

import _root_.Messages.Announce
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub

object AdaptorFWK {
  val ActorName  = "adaptor-fwk"
}

class AdaptorFWK extends Actor with ActorLogging {

  var adaptors = Map.empty[String, ActorRef]

  val cluster = Cluster(context.system)

  lazy val eventManager = context.actorSelection("/user/" + EventManager.EventManagerActorName)

  override def receive: Receive = {
    case Announce(neType) =>
      log.info(s"Receive New Adaptor $sender , neType=$neType")
      adaptors += (neType -> sender())
      informClusterAboutNewAdaptor(neType)
  }

  def informClusterAboutNewAdaptor(neType: String): Unit = {
    eventManager ! Publis
  }
}

object EventManager {
  val EventManagerActorName = "event-manger"
  val TopicName  = "content"
}

// Provides Publish side (in PubSub ) on med tier within a cluster
class EventManager extends Actor with ActorLogging {
  import EventManager._
  import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
  val mediator = DistributedPubSub(context.system).mediator
  // subscribe to the topic named "content"
  mediator ! Subscribe(TopicName, self)

  override def receive: Actor.Receive = {

  }
}