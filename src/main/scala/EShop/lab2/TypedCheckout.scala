package EShop.lab2

import EShop.lab3.{TypedOrderManager, TypedPayment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                       extends Command
  case class SelectDeliveryMethod(method: String)                                                 extends Command
  case object CancelCheckout                                                                      extends Command
  case object ExpireCheckout                                                                      extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command
  case object ExpirePayment                                                                       extends Command
  case object ConfirmPaymentReceived                                                              extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event

  def apply(
    cartActor: ActorRef[TypedCartActor.Command]
  ): Behavior[Command] = new TypedCheckout(cartActor).start
}

class TypedCheckout(
 cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case StartCheckout =>
          selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))

        case _ => Behaviors.same
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case SelectDeliveryMethod(_) => selectingPaymentMethod(timer)

    case ExpireCheckout | CancelCheckout => cancelled

    case _ => Behaviors.same
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case SelectPayment(payment, orderManagerRef) =>
          orderManagerRef ! TypedOrderManager.ConfirmPaymentStarted(
            context.spawn(TypedPayment(payment, orderManagerRef, context.self), "payment")
          )
          processingPayment(context.scheduleOnce(checkoutTimerDuration, context.self, CancelCheckout))

        case ExpireCheckout | CancelCheckout => cancelled

        case _ => Behaviors.same
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case ExpirePayment | CancelCheckout => cancelled

    case ConfirmPaymentReceived => closed

    case _ => Behaviors.same
  }

  def cancelled: Behavior[TypedCheckout.Command] = {
    cartActor ! TypedCartActor.ConfirmCheckoutClosed
    Behaviors.stopped
  }

  def closed: Behavior[TypedCheckout.Command] = {
    cartActor ! TypedCartActor.ConfirmCheckoutClosed
    Behaviors.stopped
  }

}
