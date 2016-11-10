package app.tier

import common.{Events, Messages}
import common.Events._
import common.Messages._
import akka.actor.{Actor, ActorLogging, ActorRef, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import med.tier.AdaptorFWK


class MTM extends Actor with ActorLogging {

  val queueName: String = "DefaultQueueName"

  val cluster = Cluster(context.system)

  /** Contain all adaptors in the cluster, (key=NeType; value=List of addresses to available adaptors) */
  var adaptors = Map.empty[String, List[ActorRef]]

  /** key=meSelf , value=AdaptorAddress */
  var routingTable = Map.empty[String, ActorRef]

  import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
  val mediator = DistributedPubSub(context.system).mediator

  // subscribe to cluster changes
  override def preStart(): Unit = {
    // subscribe to the common topic named "content"
    mediator ! Subscribe(AdaptorFWK.TopicName, self)

    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])

    log.info("MTM is STARTED...")
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Actor.Receive = mtmApi orElse clusterListener orElse mtmInternalCommunication orElse eventsReceive

  override def unhandled(msg: Any): Unit = {
    println(" MTM Received unhandled message" + msg)
  }

  /** @return true if this node is a leader in group app-tier; false otherwise */
  def imALeader(): Boolean = {
    val roleLeader = cluster.state.roleLeader("app-tier")
    log.info("Role Leader" + roleLeader)
    if (roleLeader.isDefined) roleLeader.get == cluster.selfAddress else false
  }

  def mtmApi: Receive = {
    case GetAllAdaptors =>
      sender() ! AllAdaptors(adaptors)

    case GetAdaptorAddress(meSelf) =>
      if (routingTable contains meSelf) {
        sender() ! AdaptorAddress(meSelf, routingTable(meSelf))
      } else {
        sender() ! NoAdaptors
      }

    case CreateMe(neId, neType) =>
      val adaptor = adaptors(neType).head
      adaptor ! BindNe(neId, queueName)
    case DeleteMe(neId) =>
      val adaptorAddress = routingTable(neId)
      adaptorAddress ! UnbindNe(neId)
  }

  def mtmInternalCommunication: Receive = {
    case state@GlobalRoutingState(adaptorsState, routingTableState) =>
      log.info(s"Receiving global routing state=$state from=${sender()}")
      adaptors = adaptorsState
      routingTable = routingTableState

  }

  def clusterListener: Receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      if (member.hasRole("app-tier") && imALeader()) {
        val state = GlobalRoutingState(adaptors, routingTable)
        log.info(s"I'm a leader. Sending routing state=$state to the new member=${member.address}")
        context.system.actorSelection(RootActorPath(member.address) / "user" / "MTM") ! GlobalRoutingState(adaptors, routingTable)
      }

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

  def eventsReceive: Receive = {
    case evt: NCEvent =>
      log.info(s"MTM receive event $evt ")
      handleEvent(evt)
    case SubscribeAck(Subscribe(AdaptorFWK.TopicName, None, _)) ⇒
      log.info("MTM subscribing to " + AdaptorFWK.TopicName)
  }

  def handleEvent(evt: NCEvent): Unit = evt match {
    case evt@NewAdaptor(neType, address) =>
      adaptors += (neType -> List(address))
    case AdaptorRemoved(neType, adaptorAddress) =>
      adaptors -= neType
      // Right now we flush routing table
      routingTable = routingTable.filterNot(_._2 == adaptorAddress)
      // TBD: Leader heve to migrate all MEs to other adaptor instances in cluster
    case evt@NeBounded(neId, neType, adaptor) =>
      routingTable += (neId -> adaptor)
    case evt@NeUnBounded(neId, adaptor) =>
      routingTable -= neId
  }
}


// You register actors to the local mediator with DistributedPubSubMediator.Subscribe.
// Successful Subscribe and Unsubscribe is acknowledged with DistributedPubSubMediator.SubscribeAck
// and DistributedPubSubMediator.UnsubscribeAck replies.
// The acknowledgment means that the subscription is registered, but it can still take some time until it is replicated to other nodes.
