import java.util.UUID

import Messages.Announce
import akka.actor.{Actor, Props}

import scala.concurrent.duration._

object Adaptor {
  def props(neType: String): Props = Props(new Adaptor(neType))

  private case object Tick

  abstract class BaseEvent { def evtId: Long }

  case class Event(evtId: Long, neId: String, neType: String, payload: String) extends BaseEvent

  val DefaultNEId = "/mit/md/1111111/me/1111111"
}

class Adaptor(neType: String) extends Actor {

  import Adaptor._

  val tickTask = context.system.scheduler.schedule(3.seconds, 3.seconds, self, Tick)

  override def preStart(): Unit = {
    context.actorSelection(self.path.parent / AdaptorFWK.ActorName) ! Announce(neType)
  }

  override def postStop(): Unit = {
    tickTask.cancel()
  }

  var n = 0

  override def receive: Receive = {
    case Tick =>
      n += 1
      generateEvent(n)
  }

  def generateEvent(evtId: Long): Unit = {
    val evt = Event(evtId, DefaultNEId, neType, UUID.randomUUID().toString)
    println(s"Generating Event -> $evt")
    context.system.eventStream.publish(evt)
  }

}
