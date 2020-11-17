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
        case CheckoutStarted => selectingDelivery(scheduler.scheduleOnce(timerDuration, self, ExpireCheckout))
        case DeliveryMethodSelected(_) =>
          selectingPaymentMethod(scheduler.scheduleOnce(timerDuration, self, ExpireCheckout))
        case CheckOutClosed    => closed
        case CheckoutCancelled => cancelled
        case PaymentStarted(_) => processingPayment(scheduler.scheduleOnce(timerDuration, self, ExpirePayment))

      }
    )
  }

  def receiveCommand: Receive = LoggingReceive {
    case StartCheckout => persist(CheckoutStarted)(updateState(_))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) => persist(DeliveryMethodSelected(method))(updateState(_, Some(timer)))

    case ExpireCheckout | CancelCheckout => persist(CheckoutCancelled)(updateState(_))
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(payment) =>
      sender ! OrderManager.ConfirmPaymentStarted(context.actorOf(Payment.props(payment, sender, self), "payment"))
      persist(PaymentStarted(self))(updateState(_, Some(timer)))

    case ExpireCheckout | CancelCheckout => persist(CheckoutCancelled)(updateState(_))
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ConfirmPaymentReceived => persist(CheckOutClosed)(updateState(_))

    case ExpirePayment | ExpireCheckout | CancelCheckout => persist(CheckoutCancelled)(updateState(_))
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

  override def receiveRecover: Receive = LoggingReceive {
    case event: Event => updateState(event)
  }
}
