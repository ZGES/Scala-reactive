package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object TypedOrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[TypedPayment.Command])                        extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class TypedOrderManager {

  import TypedOrderManager._

  def start: Behavior[TypedOrderManager.Command] = uninitialized

  def uninitialized: Behavior[TypedOrderManager.Command] =
    Behaviors.setup(context => open(context.spawn(TypedCartActor(), "cart")))

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[TypedOrderManager.Command] =
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case AddItem(id, sender) =>
            cartActor ! TypedCartActor.AddItem(id)
            sender ! Done
            Behaviors.same

          case RemoveItem(id, sender) =>
            cartActor ! TypedCartActor.RemoveItem(id)
            sender ! Done
            Behaviors.same

          case Buy(sender) =>
            cartActor ! TypedCartActor.StartCheckout(context.self)
            inCheckout(cartActor, sender)

          case _ => Behaviors.same
        }
    )

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[TypedOrderManager.Command] =
    Behaviors.receiveMessage {
      case ConfirmCheckoutStarted(checkoutRef) =>
        senderRef ! Done
        inCheckout(checkoutRef)

      case _ => Behaviors.same
    }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[TypedOrderManager.Command] =
    Behaviors.receive(
      (context, msg) =>
        msg match {
          case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
            checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
            checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
            inPayment(sender)

          case _ => Behaviors.same
        }
    )

  def inPayment(senderRef: ActorRef[Ack]): Behavior[TypedOrderManager.Command] =
    Behaviors.receiveMessage {
      case ConfirmPaymentStarted(paymentRef) =>
        senderRef ! Done
        inPayment(paymentRef, senderRef)

      case ConfirmPaymentReceived =>
        senderRef ! Done
        finished

      case _ => Behaviors.same
  }

  def inPayment(
    paymentActorRef: ActorRef[TypedPayment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[TypedOrderManager.Command] =
    Behaviors.receiveMessage {
      case Pay(sender) =>
        paymentActorRef ! TypedPayment.DoPayment
        inPayment(sender)

      case _ => Behaviors.same
    }

  def finished: Behavior[TypedOrderManager.Command] = Behaviors.stopped
}
