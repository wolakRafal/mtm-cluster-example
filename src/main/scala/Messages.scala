import akka.remote.ContainerFormats.ActorRef

object Messages {


  final case class GetAdaptorAddress(meSelfUri: String)

  final case class AdaptorAddress(meSelfUri: String, adaptor: ActorRef)

  final case class NoAdaptors(meSelf: String, reason: String)

  /** Adaptor uses this message to announce himself to adaptor framework **/
  final case class Announce(neType: String)


}
