package EShop.lab5

import java.net.URI

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat: JsonFormat[URI] = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val itemFormat: RootJsonFormat[ProductCatalog.Item]   = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat: RootJsonFormat[ProductCatalog.Items] = jsonFormat1(ProductCatalog.Items)
}

object ProductCatalogHttpServerApp extends App {
  new ProductCatalogServer().startServer("localhost", 9000)
}

class ProductCatalogServer extends HttpApp with JsonSupport {

  implicit val timeout: Timeout = Timeout(2 seconds)

  override protected def routes: Route = {
    path("items") {
      get {
        parameters("brand", "names") { (brand, names) =>
          val catalog =
            systemReference.get().actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2553/user/productcatalog")
          val future = catalog ? ProductCatalog.GetItems(brand, names.split(",").toList)
          val result = Await.result(future, timeout.duration).asInstanceOf[ProductCatalog.Items]
          complete(result)
        }
      }
    }
  }
}
