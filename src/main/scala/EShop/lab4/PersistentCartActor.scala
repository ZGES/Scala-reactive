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
        case CartExpired | CheckoutClosed       => empty
        case CheckoutCancelled(cart)            => nonEmpty(cart, timer.get)
        case ItemAdded(item, cart)              => nonEmpty(cart.addItem(item), timer.get)
        case CartEmptied                        => empty
        case ItemRemoved(item, cart)            => nonEmpty(cart.removeItem(item), timer.get)
        case CheckoutStarted(_, cart)           => inCheckout(cart)
      }
    )
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart.empty)) { event =>
        updateState(event, Option(scheduleTimer))
      }

    case GetItems =>
      sender ! Cart.empty
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, cart)) { event =>
        updateState(event, Option(scheduleTimer))
      }

    case RemoveItem(item) if cart contains item =>
      if (cart.size == 1) persist(CartEmptied) { event =>
        updateState(event)
      }
      else persist(ItemRemoved(item, cart)) { event =>
        updateState(event, Option(scheduleTimer))
      }

    case GetItems =>
      sender ! cart

    case ExpireCart =>
      persist(CartExpired) { event =>
        updateState(event)
      }

    case StartCheckout =>
      val checkout = context.actorOf(Checkout props self, name = "checkout")
      persist(CheckoutStarted(checkout, cart)) { event =>
        checkout ! Checkout.StartCheckout
        sender ! OrderManager.ConfirmCheckoutStarted(checkout)
        updateState(event)
      }
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      persist(CheckoutCancelled(cart)) { event =>
        updateState(event, Option(scheduleTimer))
      }

    case ConfirmCheckoutClosed =>
      persist(CheckoutClosed) { event =>
        updateState(event)
      }

    case GetItems =>
      sender ! cart
  }

  override def receiveRecover: Receive = {
    case (evt: Event, timer: Option[Cancellable]) => updateState(evt, timer)
  }
}
