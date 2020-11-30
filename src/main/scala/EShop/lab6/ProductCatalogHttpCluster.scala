package EShop.lab6


import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import scala.util.Try

object ClusterNodeApp extends App {
  private val config = ConfigFactory.load()

  val system = ActorSystem(
    "ClusterPCRouters",
    config
      .getConfig(Try(args(0)).getOrElse("cluster-default"))
      .withFallback(config.getConfig("cluster-default"))
  )
}


object ProductCatalogHttpServerInClusterApp extends App {
  new ProductCatalogServerInCluster().startServer("localhost", args(0).toInt)
}

class ProductCatalogServerInCluster extends HttpApp with EShop.lab5.JsonSupport {

  private val config = ConfigFactory.load()

  val system: ActorSystem = ActorSystem(
    "ClusterPCRouters",
    config.getConfig("cluster-default")
  )

  val catalogs: ActorRef = system.actorOf(
    ClusterRouterPool(
      RoundRobinPool(0),
      ClusterRouterPoolSettings(totalInstances = 100, maxInstancesPerNode = 3, allowLocalRoutees = true)
    ).props(ProductCatalog.props(new SearchService())),
    name = "clusterPCRouter"
  )

  implicit val timeout: Timeout = Timeout(5 seconds)

  override protected def routes: Route = {
    path("items") {
      get {
        parameters("brand", "names") { (brand, names) =>
          val future = catalogs ? ProductCatalog.GetItems(brand, names.split(",").toList)
          val result = Await.result(future, timeout.duration).asInstanceOf[ProductCatalog.Items]
          complete(result)
        }
      }
    }
  }
}
