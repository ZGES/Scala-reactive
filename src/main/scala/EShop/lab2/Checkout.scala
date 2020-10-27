package EShop.lab2

import EShop.lab2.CartActor.ConfirmCheckoutClosed
import EShop.lab2.Checkout._
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
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef): Props = Props(new Checkout())
}

class Checkout extends Actor {

  private val scheduler = context.system.scheduler
  private val log = Logging(context.system, this)

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  private def checkoutTimer: Cancellable = context.system.scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def paymentTimer: Cancellable = context.system.scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context become selectingDelivery(checkoutTimer)
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      sender ! SelectingDeliveryStarted(timer)
      context become selectingPaymentMethod(checkoutTimer)

    case ExpireCheckout =>
      context become cancelled

    case CancelCheckout =>
      context become cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(payment) =>
      context become processingPayment(paymentTimer)

    case ExpireCheckout =>
      context become cancelled

    case CancelCheckout =>
      context become cancelled
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive{
    case ConfirmPaymentReceived =>
      context become closed

    case ExpirePayment =>
      context become cancelled
  }

  def cancelled: Receive = LoggingReceive {
    case
  }

  def closed: Receive = LoggingReceive{
    case ConfirmPaymentReceived =>
      context.parent ! ConfirmCheckoutClosed
  }

}
