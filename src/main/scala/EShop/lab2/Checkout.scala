package EShop.lab2

import EShop.lab2.Checkout._
import EShop.lab3.{OrderManager, Payment}
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                        extends Event
  case class PaymentStarted(payment: ActorRef)      extends Event
  case object CheckoutStarted                       extends Event
  case object CheckoutCancelled                     extends Event
  case class DeliveryMethodSelected(method: String) extends Event

  def props(cart: ActorRef): Props = Props(new Checkout(cart))
}

class Checkout(
  cartActor: ActorRef
) extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context become selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(_) =>
      context become selectingPaymentMethod(timer)

    case ExpireCheckout | CancelCheckout =>
      context become cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(method) =>
      val payment = context.actorOf(Payment.props(method, sender, self), name = "payment")
      sender ! OrderManager.ConfirmPaymentStarted(payment)
      context become processingPayment(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))

    case ExpireCheckout | CancelCheckout =>
      context become cancelled
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ConfirmPaymentReceived =>
      context become closed

    case ExpirePayment | CancelCheckout =>
      context become cancelled
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

}
