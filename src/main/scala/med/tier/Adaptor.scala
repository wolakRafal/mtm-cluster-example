package med.tier

import akka.actor._
import common.Messages._

import scala.concurrent.duration._

object Adaptor {
  def props(neType: String): Props = Props(new Adaptor(neType))

  private case class Tick(neId: String, neType: String, topicName: String)

  abstract class BaseEvent { def evtId: Long }

  case class Event(evtId: Long, neId: String, neType: String, topicName: String) extends BaseEvent

  val DefaultNEId = "/mit/md/1111111/me/1111111"
}

class Adaptor(neType: String) extends Actor with ActorLogging {

  import Adaptor._

  implicit val ec = context.dispatcher

  var adaptorFwk: ActorSelection = _

  override def preStart(): Unit = {
    adaptorFwk = context.system.actorSelection("/user/adaptor-fwk")
    adaptorFwk ! Announce(neType)
  }

  var connectedNes = Map.empty[String, Cancellable]

  override def postStop(): Unit = {
    connectedNes.values.map(_.cancel())
  }

  var n = 0

  override def receive: Receive = {
    case BindNe(neId, topic) =>
      // start generating events
      val tickTask = context.system.scheduler.schedule(3.seconds, 3.seconds, self, Tick(neId, neType, topic))
      connectedNes += (neId -> tickTask)
      adaptorFwk ! Bounded(neId, neType)

    case UnbindNe(neId) =>
      connectedNes(neId).cancel()
      connectedNes -= neId
      adaptorFwk ! UnBounded(neId)

    case t: Tick =>
      n += 1
      generateEvent(n, t)
  }

  private def generateEvent(evtId: Long, t: Tick): Unit = {
    val evt = Event(evtId, t.neId, t.neType, t.topicName)
    println(s"Generating Event -> $evt")
    context.system.eventStream.publish(evt)
  }

}
