package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object TypedPayment {

  sealed trait Command
  case object DoPayment extends Command

  def apply(
    method: String,
    orderManager: ActorRef[TypedOrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Behavior[Command] = new TypedPayment(method, orderManager, checkout).start
}

class TypedPayment(
  method: String,
  orderManager: ActorRef[TypedOrderManager.Command],
  checkout: ActorRef[TypedCheckout.Command]
) {

  import TypedPayment._

  def start: Behavior[TypedPayment.Command] = Behaviors.receiveMessage {
    case DoPayment =>
      orderManager ! TypedOrderManager.ConfirmPaymentReceived
      checkout ! TypedCheckout.ConfirmPaymentReceived
      Behaviors.same

    case _ => Behaviors.same
  }

}
