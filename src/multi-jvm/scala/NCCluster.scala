import akka.actor._
import akka.cluster.Cluster
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import app.tier.MTM
import com.typesafe.config.ConfigFactory
import common.Messages
import med.tier.{AdaptorFWK, Adaptor}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

object NCCluster extends MultiNodeConfig {
  val app1 = role("AppTier1")
//  val app2 = role("AppTier2")
//  val app3 = role("AppTier3")

  val med1 = role("MedTier1")
  val med2 = role("MedTier2")
//  val med3 = role("MedTier3")

  def appNodes = Seq(app1)//, app2, app3)
  def medNodes = Seq(med1, med2) // med3)
  def nodeList = appNodes ++ medNodes

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.remote.log-remote-lifecycle-events = off
    akka.loglevel = "INFO"
    akka.extensions = ["akka.cluster.pubsub.DistributedPubSub"]
                                         """))

  nodeConfig(appNodes: _*)(
    ConfigFactory.parseString("akka.cluster.roles =[app-tier]"))

  nodeConfig(medNodes: _*)(
    ConfigFactory.parseString("akka.cluster.roles =[med-tier]"))
}

// need one concrete test class per node
class NCClusterSpecMultiJvmNode1 extends NCCluster
class NCClusterSpecMultiJvmNode2 extends NCCluster
class NCClusterSpecMultiJvmNode3 extends NCCluster
//class NCClusterSpecMultiJvmNode4 extends NCClusterSpec
//class NCClusterSpecMultiJvmNode5 extends NCClusterSpec
//class NCClusterSpecMultiJvmNode6 extends NCClusterSpec



abstract class NCCluster extends MultiNodeSpec(NCCluster)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  import NCCluster._
  import Messages._

  override def initialParticipants: Int = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  def ensureStarted(startedActor: ActorRef): Unit = {
    startedActor ! Identify("Are U Started?")
    expectMsgPF() {
      case ActorIdentity("Are U Started?", _) =>
    }
  }


  "The NC cluster sample " must {
    "illustrate how to start first app tier node" in within(5.seconds) {
      runOn(app1) {
        Cluster(system) join node(app1).address

        val mtmActor = system.actorOf(Props[MTM], name = "MTM")
        mtmActor ! Identify("mtm")

        expectMsgPF() {
          case ActorIdentity(_,_) =>
        }

        mtmActor ! GetAllAdaptors
        expectMsgPF() {
          case AllAdaptors(m) =>
            m should have size 0
        }
        mtmActor ! GetAdaptorAddress("/non/existent/me/address")
        expectMsgPF() {
          case "NOT IMPLEMENTED" =>
        }

      }
      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("app1-started")
    }

    "illustrate how a med tier node automatically registers" in within(15.seconds) {

      runOn(med1, med2) {
        Cluster(system) join node(app1).address
        Thread.sleep(5000L)
        ensureStarted(system.actorOf(Props[AdaptorFWK], name = AdaptorFWK.ActorName))
        Thread.sleep(1000L)
        ensureStarted(system.actorOf(Adaptor.props("f8"), name = "adaptor-f8"))
        ensureStarted(system.actorOf(Adaptor.props("f7"), name = "adaptor-f7"))
        ensureStarted(system.actorOf(Adaptor.props("f16"), name = "adaptor-f16"))
      }


      enterBarrier("med1-started")

      runOn(app1) {
        Thread.sleep(1000L)
        system.actorSelection(RootActorPath(node(app1).address).root / "user" / "MTM") ! GetAllAdaptors
        expectMsgPF() {
          case AllAdaptors(adaptorsMap) =>
            adaptorsMap should have size 3
            adaptorsMap should contain key "f8"
            adaptorsMap should contain key "f7"
            adaptorsMap should contain key "f16"
        }
      }
      enterBarrier("Test finished")
    }

    "illustrate how more nodes registers 3 x 3" in within(20.seconds) {

    }

    "illustrate how to install Adaptor in Med tier" in within(20.seconds) {

    }

    "illustrate how to install many adaptor instances on each Med Tier" in within(20.seconds) {

    }



  }

  "The MTM " must {
    "Illustrate Stable Hashing routing to adaptors" in within(20.seconds) {

    }

    "Illustrate round robin routing to adaptors" in within(20.seconds) {

    }


  }
  

}
