package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
    with AnyFlatSpecLike
    with ImplicitSender
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {
    val cartProbe   = TestProbe()
    val checkoutRef = cartProbe.childActorOf(Checkout.props(cartProbe.ref))

    cartProbe.send(checkoutRef, Checkout.StartCheckout)
    cartProbe.send(checkoutRef, Checkout.SelectDeliveryMethod("post"))
    cartProbe.send(checkoutRef, Checkout.SelectPayment("cash"))
    cartProbe.expectMsgType[OrderManager.ConfirmPaymentStarted]
    cartProbe.send(checkoutRef, Checkout.ConfirmPaymentReceived)
    cartProbe.expectMsg(CartActor.ConfirmCheckoutClosed)
  }
}
