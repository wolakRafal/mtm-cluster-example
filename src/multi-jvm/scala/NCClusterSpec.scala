import akka.actor.Props
import akka.cluster.Cluster
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

object NCClusterSpec extends MultiNodeConfig {
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
    akka.actor.provider = cluster
    akka.remote.log-remote-lifecycle-events = off
                                         """))
  
  nodeConfig(app1, app2, app3)(
    ConfigFactory.parseString("akka.cluster.roles =[app-tier]"))

  nodeConfig(med1, med2, med3)(
    ConfigFactory.parseString("akka.cluster.roles =[med-tier]"))
}

// need one concrete test class per node
class TransformationSampleSpecMultiJvmNode1 extends NCClusterSpec 
class TransformationSampleSpecMultiJvmNode2 extends NCClusterSpec 
class TransformationSampleSpecMultiJvmNode3 extends NCClusterSpec 
class TransformationSampleSpecMultiJvmNode4 extends NCClusterSpec 
class TransformationSampleSpecMultiJvmNode5 extends NCClusterSpec 



abstract class NCClusterSpec extends MultiNodeSpec(NCClusterSpec)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  import NCClusterSpec._
  import Messages._

  override def initialParticipants: Int = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()
  
  "The NC cluster sample " must {
    "illustrate how to start first app tier node" in within(15 seconds) {
      runOn(app1) {
        Cluster(system) join node(app1).address
        val mtmActor = system.actorOf(Props[MTM], name = "MTM")
        mtmActor ! GetAdaptorAddress("/non/existent/me/address")
        expectMsgPF() {
          case NoAdaptors("/non/existent/me/address", _) =>
        }

      }
      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("app1-started")
    }

    "illustrate how a med tier node automatically registers" in within(15 seconds) {
      runOn(med1) {
        Cluster(system) join node(app1).address
        system.actorOf(Props[AdaptorFWK], name = "adaptor-framework")
      }
      testConductor.enter("med1-started")

      runOn(app1) {

      }
    }

    "illustrate how more nodes registers 3 x 3" in within(20 seconds) {

    }

    "illustrate how to install Adaptor in Med tier" in within(20 seconds) {

    }

    "illustrate how to install many adaptor instances on each Med Tier" in within(20 seconds) {

    }



  }

  "The MTM " must {
    "Illustrate Stable Hashing routing to adaptors" in within(20 seconds) {

    }

    "Illustrate round robin routing to adaptors" in within(20 seconds) {

    }



  }
  

}
