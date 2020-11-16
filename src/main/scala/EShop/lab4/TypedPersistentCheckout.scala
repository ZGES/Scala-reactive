package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab3.{TypedOrderManager, TypedPayment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class TypedPersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  def schedule(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(timerDuration, context.self, ExpireCheckout)

  def apply(cartActor: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart =>
        command match{
          case StartCheckout => Effect.persist(CheckoutStarted)

          case _ => Effect.none
        }

      case SelectingDelivery(_) =>
        command match{
          case SelectDeliveryMethod(method) => Effect.persist(DeliveryMethodSelected(method))

          case ExpireCheckout | CancelCheckout => Effect.persist(CheckoutCancelled)

          case _ => Effect.none
        }

      case SelectingPaymentMethod(_) =>
        command match{
          case SelectPayment(payment, orderManagerRef) =>
            val paymentRef = context.spawn(TypedPayment(payment, orderManagerRef, context.self), "payment")
            orderManagerRef ! TypedOrderManager.ConfirmPaymentStarted(paymentRef)
            Effect.persist(PaymentStarted(paymentRef))

          case ExpireCheckout | CancelCheckout => Effect.persist(CheckoutCancelled)

          case _ => Effect.none
        }

      case ProcessingPayment(_) =>
        command match{
          case ExpirePayment | CancelCheckout => Effect.persist(CheckoutCancelled)

          case ConfirmPaymentReceived => Effect.persist(CheckOutClosed)

          case _ => Effect.none
        }

      case Cancelled =>
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        Effect.none


      case Closed =>
        cartActor ! TypedCartActor.ConfirmCheckoutClosed
        Effect.none
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted           => SelectingDelivery(schedule(context))
      case DeliveryMethodSelected(_) => SelectingPaymentMethod(state.timerOpt.get)
      case PaymentStarted(_)         => ProcessingPayment(context.scheduleOnce(timerDuration, context.self, ExpirePayment))
      case CheckOutClosed            => Closed
      case CheckoutCancelled         => Cancelled
    }
  }
}
