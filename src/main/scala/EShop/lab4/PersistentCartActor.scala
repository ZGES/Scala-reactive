package EShop.lab4

import EShop.lab2.{Cart, Checkout}
import EShop.lab3.OrderManager
import akka.actor.{Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PersistentCartActor {

  def props(persistenceId: String): Props = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.CartActor._

  private val log                       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {
    context.become(
      event match {
        case CartExpired | CheckoutClosed => empty
        case CheckoutCancelled(cart)      => nonEmpty(cart, scheduleTimer)
        case ItemAdded(item, cart)        => nonEmpty(cart.addItem(item), scheduleTimer)
        case CartEmptied                  => empty
        case ItemRemoved(item, cart)      => nonEmpty(cart.removeItem(item), scheduleTimer)
        case CheckoutStarted(_, cart)     => inCheckout(cart)
      }
    )
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) => persist(ItemAdded(item, Cart.empty))(updateState(_))
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) => persist(ItemAdded(item, cart))(updateState(_))

    case RemoveItem(item) if cart contains item =>
      val newCart = cart removeItem item
      if (newCart.size == 0) persist(CartEmptied)(updateState(_))
      else persist(ItemRemoved(item, cart))(updateState(_))

    case ExpireCart => persist(CartExpired)(updateState(_))

    case StartCheckout =>
      val checkout = context.actorOf(Checkout props self, name = "checkout")
      checkout ! Checkout.StartCheckout
      sender ! OrderManager.ConfirmCheckoutStarted(checkout)
      persist(CheckoutStarted(checkout, cart))(updateState(_))
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled => persist(CheckoutCancelled(cart))(updateState(_))

    case ConfirmCheckoutClosed => persist(CheckoutClosed)(updateState(_))
  }

  override def receiveRecover: Receive = {
    case event: Event => updateState(event)
  }
}
