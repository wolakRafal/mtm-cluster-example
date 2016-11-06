import Messages.Announce
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.actor.Actor.Receive


object AdaptorFWK {
  val ActorName  = "adaptor-fwk"
}

class AdaptorFWK extends Actor with ActorLogging {

  var adaptors = Map.empty[String, ActorRef]


  override def receive: Receive = {
    case Announce(neType) =>
      log.info(s"Receive New Adaptor $sender , neType=$neType")
      adaptors += (neType -> sender())
      informClusterAboutNewAdaptor(neType)
  }

  def informClusterAboutNewAdaptor(neType: String): Unit = {

  }

}
