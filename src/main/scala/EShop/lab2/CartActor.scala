package EShop.lab2

import EShop.lab3.OrderManager
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command
  case object GetItems                 extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef, cart: Cart) extends Event
  case class ItemAdded(itemId: Any, cart: Cart)                 extends Event
  case class ItemRemoved(itemId: Any, cart: Cart)               extends Event
  case object CartEmptied                                       extends Event
  case object CartExpired                                       extends Event
  case object CheckoutClosed                                    extends Event
  case class CheckoutCancelled(cart: Cart)                      extends Event

  def props: Props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log                       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      val cart = Cart.empty
      context become nonEmpty(cart.addItem(item), scheduleTimer)

    case GetItems =>
      sender ! Cart.empty
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(cart.addItem(item), scheduleTimer)

    case RemoveItem(item) if cart contains item =>
      val newCart = cart.removeItem(item)
      if (newCart.size == 0) context become empty
      else context become nonEmpty(newCart, scheduleTimer)

    case GetItems =>
      sender ! cart

    case ExpireCart =>
      context become empty

    case StartCheckout =>
      val checkout = context.actorOf(Checkout props self, name = "checkout")
      checkout ! Checkout.StartCheckout
      sender ! OrderManager.ConfirmCheckoutStarted(checkout)
      context become inCheckout(cart)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      context become nonEmpty(cart, scheduleTimer)

    case ConfirmCheckoutClosed =>
      context become empty

    case GetItems =>
      sender ! cart
  }

}
