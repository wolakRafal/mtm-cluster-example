package med.tier

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import common.Events.NewAdaptor
import common.Messages.Announce
import med.tier.AdaptorFWK._

object AdaptorFWK {
  val ActorName  = "adaptor-fwk"
  val TopicName  = "content"
}

class AdaptorFWK extends Actor with ActorLogging {

  var adaptors = Map.empty[String, ActorRef]
  val cluster = Cluster(context.system)

  import DistributedPubSubMediator.Publish
  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator

  override def receive: Receive = {
    case Announce(neType) =>
      log.info(s"Receive New Adaptor $sender , neType=$neType")
      adaptors += (neType -> sender())
      informClusterAboutNewAdaptor(neType, sender())
  }

  def informClusterAboutNewAdaptor(neType: String, adaptor: ActorRef): Unit = {
    mediator ! Publish(TopicName, NewAdaptor(neType, adaptor))
  }
}

