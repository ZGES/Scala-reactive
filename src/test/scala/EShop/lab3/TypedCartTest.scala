package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val probe     = testKit.createTestProbe[Cart]()
    val cartActor = testKit.spawn(TypedCartActor())

    cartActor ! AddItem("banana")
    cartActor ! GetItems(probe.ref)

    val expectedCart = Cart.empty.addItem("banana")
    probe.expectMessage(expectedCart)
  }

  it should "be empty after adding and removing the same item" in {
    val probe     = testKit.createTestProbe[Cart]()
    val cartActor = testKit.spawn(TypedCartActor())

    cartActor ! AddItem("orange")
    cartActor ! RemoveItem("orange")
    cartActor ! GetItems(probe.ref)
    probe.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    val probe     = testKit.createTestProbe[TypedOrderManager.Command]()
    val cartActor = testKit.spawn(TypedCartActor())

    cartActor ! AddItem("orange")
    cartActor ! AddItem("banana")
    cartActor ! StartCheckout(probe.ref)
    probe.expectMessageType[TypedOrderManager.ConfirmCheckoutStarted]
  }
}
