package EShop.lab4

import EShop.lab2.CartActor
import EShop.lab3.{OrderManager, Payment}
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String): Props =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._
  private val scheduler             = context.system.scheduler
  private val log                   = Logging(context.system, this)
  val timerDuration: FiniteDuration = 1.seconds

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    context.become(
      event match {
        case CheckoutStarted                => selectingDelivery(maybeTimer.get)
        case DeliveryMethodSelected(_)      => selectingPaymentMethod(maybeTimer.get)
        case CheckOutClosed                 => closed
        case CheckoutCancelled              => cancelled
        case PaymentStarted(_)              => processingPayment(maybeTimer.get)

      }
    )
  }

  def receiveCommand: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted) { event =>
        updateState(event, Option(scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)))
      }
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      persist(DeliveryMethodSelected(method)) { event =>
        updateState(event, Option(timer))
      }

    case ExpireCheckout | CancelCheckout =>
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(method) =>
      val payment = context.actorOf(Payment.props(method, sender, self), name = "payment")
      persist(PaymentStarted(payment)) { event =>
        sender ! OrderManager.ConfirmPaymentStarted(payment)
        updateState(event, Option(scheduler.scheduleOnce(timerDuration, self, ExpirePayment)))
      }

    case ExpireCheckout | CancelCheckout =>
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ConfirmPaymentReceived =>
      persist(CheckOutClosed) { event =>
        updateState(event)
      }

    case ExpireCheckout | CancelCheckout =>
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def cancelled: Receive = LoggingReceive {
    case _ =>
      cartActor ! CartActor.ConfirmCheckoutClosed
      context stop self
  }

  def closed: Receive = LoggingReceive {
    case _ =>
      cartActor ! CartActor.ConfirmCheckoutClosed
      context stop self
  }

  override def receiveRecover: Receive = {
    case (evt: Event, timer: Option[Cancellable]) => updateState(evt, timer)
  }
}
