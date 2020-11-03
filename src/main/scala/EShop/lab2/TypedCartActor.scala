package EShop.lab2

import EShop.lab3.TypedOrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                              extends Command
  case class RemoveItem(item: Any)                           extends Command
  case object ExpireCart                                     extends Command
  case class StartCheckout(orderManagerRef: ActorRef[Event]) extends Command
  case object ConfirmCheckoutCancelled                       extends Command
  case object ConfirmCheckoutClosed                          extends Command
  case class GetItems(sender: ActorRef[Cart])                extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event

  def apply(): Behavior[Command] = new TypedCartActor().start
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(item) =>
          val cart = Cart.empty
          nonEmpty(cart.addItem(item), scheduleTimer(context))

        case GetItems(sender) =>
          sender ! Cart.empty
          Behaviors.same

        case _ => Behaviors.same
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case RemoveItem(item) if cart.contains(item) =>
          val newCart = cart.removeItem(item)
          if (newCart.size == 0) empty
          else nonEmpty(newCart, scheduleTimer(context))

        case AddItem(item) => nonEmpty(cart.addItem(item), scheduleTimer(context))

        case ExpireCart => empty

        case GetItems(sender) =>
          sender ! cart
          Behaviors.same

        case StartCheckout(orderManagerRef) =>
          val checkout = context.spawn(TypedCheckout(context.self), "checkout")
          checkout ! TypedCheckout.StartCheckout
          orderManagerRef ! CheckoutStarted(checkout)
          inCheckout(cart)

        case _ => Behaviors.same
    }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case GetItems(sender) =>
          sender ! cart
          Behaviors.same

        case ConfirmCheckoutClosed => empty

        case ConfirmCheckoutCancelled => nonEmpty(cart, scheduleTimer(context))

        case _ => Behaviors.same
    }
  )

}
