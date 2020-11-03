package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val checkoutProbe = testKit.createTestProbe[TypedCartActor.Command]()
    val orderProbe    = testKit.createTestProbe[TypedOrderManager.Command]()
    val cartActor     = testKit.spawn(TypedCheckout(checkoutProbe.ref))

    cartActor ! TypedCheckout.StartCheckout
    cartActor ! TypedCheckout.SelectDeliveryMethod("post")
    cartActor ! TypedCheckout.SelectPayment("cash", orderProbe.ref)
    orderProbe.expectMessageType[TypedOrderManager.ConfirmPaymentStarted]
    cartActor ! TypedCheckout.ConfirmPaymentReceived
    checkoutProbe.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }
}
