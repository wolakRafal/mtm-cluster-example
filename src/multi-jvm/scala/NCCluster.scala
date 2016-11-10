import akka.actor._
import akka.cluster.Cluster
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import app.tier.MTM
import com.typesafe.config.ConfigFactory
import common.Messages
import med.tier.{AdaptorFWK, Adaptor}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.collection.JavaConverters._

object NCCluster extends MultiNodeConfig {
  val app1 = role("AppTier1")
  val app2 = role("AppTier2")
  val app3 = role("AppTier3")

  val med1 = role("MedTier1")
  val med2 = role("MedTier2")
  val med3 = role("MedTier3")

  def appNodes = Seq(app1, app2, app3)
  def medNodes = Seq(med1, med2, med3)
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

  val meSelfTest = "/mit/md/1/me/11111111"

}

// need one concrete test class per node
class NCClusterSpecMultiJvmNode1 extends NCCluster
class NCClusterSpecMultiJvmNode2 extends NCCluster
class NCClusterSpecMultiJvmNode3 extends NCCluster
class NCClusterSpecMultiJvmNode4 extends NCCluster
class NCClusterSpecMultiJvmNode5 extends NCCluster
class NCClusterSpecMultiJvmNode6 extends NCCluster



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
    "illustrate how to start first app tier node" in within(30.seconds) {
      runOn(app1) {
        Cluster(system) join node(app1).address

        val mtmActor = system.actorOf(Props[MTM], name = "MTM")
        ensureStarted(mtmActor)

        mtmActor ! GetAllAdaptors
        expectMsgPF() {
          case AllAdaptors(m) =>
            m should have size 0
        }
        mtmActor ! GetAdaptorAddress("/non/existent/me/address")
        expectMsgPF() {
          case NoAdaptors =>
        }

      }
      // this will run on all nodes
      // use barrier to coordinate test steps
      enterBarrier("app1-started")
    }

    "illustrate how a med tier node automatically registers" in within(30.seconds) {

      runOn(med1) {
        Cluster(system) join node(app1).address
        Thread.sleep(5000L) // wait for cluster to converge
        ensureStarted(system.actorOf(Props[AdaptorFWK], name = AdaptorFWK.ActorName))

        ensureStarted(system.actorOf(Adaptor.props("f8"), name = "adaptor-f8"))
        ensureStarted(system.actorOf(Adaptor.props("f7"), name = "adaptor-f7"))
        ensureStarted(system.actorOf(Adaptor.props("f16"), name = "adaptor-f16"))
      }

      enterBarrier("med1-started")

      runOn(app1) {
        sleep(1.second)
        checkAdaptorsTable(app1)
      }
      enterBarrier("Test finished")
    }

    "illustrate how more nodes registers 3 x 3" in within(20.seconds) {
      runOn(app2, app3) {
        ensureStarted(system.actorOf(Props[MTM], name = "MTM"))
        Cluster(system) join node(app1).address
        sleep(1.second)
      }

      runOn(med2, med3) {
        Cluster(system) join node(app1).address
      }

      sleep(5.seconds) // wait for cluster to converge
      enterBarrier("All-nodes-registered")

      runOn(med3) {
        val members = Cluster(system).state.getMembers
        members.asScala should have size 6
      }
    }

  }

  "The MTM " must {
    "Leader should send adaptors and routing information to new joined MTM instance" in {

      enterBarrier("MTM-on-app2-app3-started")

      runOn(app2) {
        checkAdaptorsTable(app2)
      }
      runOn(app3) {
        checkAdaptorsTable(app3)
      }
    }


    "bind operation should be populated among all app nodes" in within(30.seconds) {
      runOn(app1) {
        system.actorSelection(RootActorPath(node(app1).address).root / "user" / "MTM") ! CreateMe(neId = meSelfTest, neType = "f8")
        sleep(15.second)
      }

      enterBarrier("me1-bound")

      // routing information should be visible on all nodes
      runOn(app1, app2, app3) {
        system.actorSelection(RootActorPath(node(app1).address).root / "user" / "MTM") ! GetAdaptorAddress(meSelfTest)
        expectMsgPF() {
          case AdaptorAddress(`meSelfTest`, adaptor) =>
            adaptor ! Identify(s"Are you connected to $meSelfTest")
        }

        expectMsgPF() {
          case ActorIdentity(_, _) =>
        }
      }
      enterBarrier("end-me-bind-test")
    }

    "unbind operation should be populated among all app nodes" in within(30.seconds) {
      runOn(app2) {
        system.actorSelection(RootActorPath(node(app2).address).root / "user" / "MTM") ! DeleteMe(meSelfTest)
        sleep(15.second)
      }

      enterBarrier("me1-unbound")

      // routing information should be visible on all nodes
      runOn(app1) {
        system.actorSelection(RootActorPath(node(app1).address).root / "user" / "MTM") ! GetAdaptorAddress(meSelfTest)
        expectMsgPF() {
          case NoAdaptors =>
        }
      }

      runOn(app2) {
        system.actorSelection(RootActorPath(node(app2).address).root / "user" / "MTM") ! GetAdaptorAddress(meSelfTest)
        expectMsgPF() {
          case NoAdaptors =>
        }
      }

      runOn(app3) {
        system.actorSelection(RootActorPath(node(app3).address).root / "user" / "MTM") ! GetAdaptorAddress(meSelfTest)
        expectMsgPF() {
          case NoAdaptors =>
        }
      }
    }

    "on removing adaptor should move rebalance other sessions" in within(30.seconds) {

      // add 3 tests ME
      runOn(app1) {
        val mtmActor = system.actorSelection(RootActorPath(node(app1).address).root / "user" / "MTM")
        mtmActor ! CreateMe(neId = meSelfTest + "1", neType = "f8")
        mtmActor ! CreateMe(neId = meSelfTest + "2", neType = "f8")
        mtmActor ! CreateMe(neId = meSelfTest + "3", neType = "f8")
        sleep(15.second)
      }

      enterBarrier("added-three-MEs-f8")

//      runOn(med1) {
//        system.actorSelection(RootActorPath(node(med1).address).root / "user" / AdaptorFWK.ActorName) ! RemoveAdaptor("f8")
//        sleep(15.second)
//      }

      enterBarrier("remove-adaptor-f8")

      // routing information should be visible on all nodes
      runOn(app1) {
        sleep(10.second)
        system.actorSelection(RootActorPath(node(app1).address).root / "user" / "MTM") ! GetAdaptorAddress(meSelfTest + "1")
        expectMsgPF() {
          case AdaptorAddress(meSelf, adaptor) /* if meSelf == meSelfTest + "1" */ =>
            adaptor ! Identify(s"Are you connected to $meSelfTest")
        }

        expectMsgPF() {
          case ActorIdentity(_, _) =>
        }
      }

      runOn(app2) {
        sleep(10.second)
        system.actorSelection(RootActorPath(node(app2).address).root / "user" / "MTM") ! GetAdaptorAddress(meSelfTest + "2")
        expectMsgPF() {
          case AdaptorAddress(meSelf, adaptor) /* if meSelf == meSelfTest + "1" */ =>
            adaptor ! Identify(s"Are you connected to $meSelfTest")
        }
        expectMsgPF() {
          case ActorIdentity(_, _) =>
        }
      }

      runOn(app3) {
        sleep(10.second)
        system.actorSelection(RootActorPath(node(app3).address).root / "user" / "MTM") ! GetAdaptorAddress(meSelfTest + "3")
        expectMsgPF() {
          case AdaptorAddress(meSelf, adaptor) /* if meSelf == meSelfTest + "1" */ =>
            adaptor ! Identify(s"Are you connected to $meSelfTest")
        }
        expectMsgPF() {
          case ActorIdentity(_, _) =>
        }
      }
    }

  }

  def checkAdaptorsTable(nodeName: RoleName)(implicit system: ActorSystem): Unit = {
    system.actorSelection(RootActorPath(node(nodeName).address).root / "user" / "MTM") ! GetAllAdaptors
    expectMsgPF() {
      case AllAdaptors(adaptorsMap) =>
        adaptorsMap should have size 3
        adaptorsMap should contain key "f8"
        adaptorsMap should contain key "f7"
        adaptorsMap should contain key "f16"
    }
  }

  def sleep(duration: FiniteDuration): Unit = Thread.sleep(duration.toMillis)

}
