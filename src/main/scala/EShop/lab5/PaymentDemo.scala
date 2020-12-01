package EShop.lab5

import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager
import EShop.lab3.Payment.DoPayment
import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object PaymentDemo extends App{

  val system = ActorSystem("paymentDemo")

  val cart = system.actorOf(Props[CartActor])
  val checkout = system.actorOf(Checkout props cart)
  val orderManager = system.actorOf(Props[OrderManager])

  val payment = system.actorOf(Payment.props("payu", orderManager, checkout))

  payment ! DoPayment

  Await.result(system.whenTerminated, Duration.Inf)
}
