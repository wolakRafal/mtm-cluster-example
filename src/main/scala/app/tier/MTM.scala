package app.tier

import _root_.Events.{NCEvent, NewAdaptor}
import _root_.Messages.{AllAdaptors, GetAdaptorAddress, GetAllAdaptors}
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import med.tier.EventManager

//object MTM {
//  def props =  ???
//}

class MTM extends Actor with ActorLogging with ClusterEventSubscriber {

  val cluster = Cluster(context.system)

  /** Contain all adaptors in the cluster, (key=NeType; value=List of addresses to available adaptors) */
  var adaptors = Map.empty[String, List[ActorRef]]

  /** key=meSelf , value=AdaptorAddress */
  var routingTable = Map.empty[String, ActorRef]

  // subscribe to cluster changes
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Actor.Receive = mtmApi orElse mtmApi

  def mtmApi: Receive = {
    case GetAllAdaptors =>
      sender() ! AllAdaptors(adaptors)

    case GetAdaptorAddress(meSelf) =>
      sender() ! "NOT IMPLEMENTED"
  }

  def clusterListener: Receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      // it can be role med-tier and role app-tier. if app-tier send state
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
      // Only Leader: get all adaptors for this member
      // get all sessions for all adaptors
      // migrate session to other MTMs instances
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is removed: {} after {}", member.address, previousStatus)
      // same as unrechable???
    case me: MemberEvent => //ignore
      log.info(s"Member event: $me")
  }

  override def handleEvent(evt: NCEvent): Unit = {
    case evt@NewAdaptor(neType, address) =>
      log.info(s"MTM receive event $evt ")
      adaptors += (neType -> List(address))
  }
}


// You register actors to the local mediator with DistributedPubSubMediator.Subscribe.
// Successful Subscribe and Unsubscribe is acknowledged with DistributedPubSubMediator.SubscribeAck
// and DistributedPubSubMediator.UnsubscribeAck replies.
// The acknowledgment means that the subscription is registered, but it can still take some time until it is replicated to other nodes.
trait ClusterEventSubscriber { this: Actor with ActorLogging =>

  import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
  val mediator = DistributedPubSub(context.system).mediator

  // subscribe to the topic named "content"
  mediator ! Subscribe(EventManager.TopicName, self)

  def handleEvent(evt : NCEvent): Unit

  def eventsReceive: Receive = {
    case evt: NCEvent =>
      log.info("Got Event {}", evt)
      handleEvent(evt)
    case SubscribeAck(Subscribe("content", None, `self`)) ⇒
      log.info("subscribing to " + EventManager.TopicName)
  }
}