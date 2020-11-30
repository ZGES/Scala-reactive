package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import EShop.lab6.ProductCatalogRouter._
import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.event.LoggingReceive
import akka.routing.{ActorRefRoutee, Router, SmallestMailboxRoutingLogic}


object ProductCatalogRouter {
  case class GetItemsDistributed(brand: String, productKeyWords: List[String])
}

class ProductCatalogRouter extends Actor with ActorLogging {

  val nbOfRoutees = 3

  val routees: Vector[ActorRefRoutee] = Vector.fill(nbOfRoutees) {
    val r = context.actorOf(ProductCatalog.props(new SearchService()))
    context watch r
    ActorRefRoutee(r)
  }

  def receive: Receive = trace(Router(SmallestMailboxRoutingLogic(), routees))

  def trace(router: Router) :Receive = LoggingReceive {
    case GetItemsDistributed(brand, productKeyWords) =>
      router.route(ProductCatalog.GetItems(brand, productKeyWords), sender())

    case Terminated(a) =>
      val r = router.removeRoutee(a)
      if (r.routees.isEmpty)
        context.system.terminate
      else
        context.become(trace(r))
  }
}

object Client {
  case object Init
}

class Client extends Actor {
  import Client._

  def receive: Receive = LoggingReceive {
    case Init =>
      val pcRouter = context.actorOf(Props(classOf[ProductCatalogRouter]), "pcr")
      pcRouter ! ProductCatalogRouter.GetItemsDistributed("gerber", List("cream"))

    case ProductCatalog.Items(items) =>
      print(items)
      context.stop(self)
  }
}

object ProductCatalogRouterDemo extends App {

  val system = ActorSystem("example")

  val client = system.actorOf(Props(classOf[Client]), "client")

  client ! Client.Init

}