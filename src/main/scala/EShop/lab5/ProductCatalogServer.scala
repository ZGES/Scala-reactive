package EShop.lab5

import java.net.URI

import EShop.lab5.ProductCatalog.Items
import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val itemsFormat = new JF[ProductCatalog.Items] {
    override def write(obj: Items): JsValue = JsString(obj.toString)

    override def read(json: JsValue): Items = json match{
      case JsString(item) => new Items(List[item])
    }
  }

  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI = json match {
      case JsString(url) => new URI(url)
      case _             => throw new RuntimeException("Parsing exception")
    }
  }

}

object ProductCatalogServerApp extends App {
  new ProductCatalogServer().startServer("localhost", 8080)
}

class ProductCatalogServer extends HttpApp with JsonSupport {

  implicit val system: ActorSystem                = ActorSystem("ProductCatalogServerSystem")
  implicit val materializer: ActorMaterializer    = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  override protected def routes: Route = {
    path("search") {
      get {
        parameters('brand.as[String], 'productKeyWords.repeated) { (brand, productKeyWords) =>
          complete{
            val productCatalogRef = system.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2554/user/productcatalog")
            implicit val timeout: Timeout = Timeout(5 seconds)
            val items = (productCatalogRef ? ProductCatalog.GetItems(brand, productKeyWords.toList)).mapTo[ProductCatalog.Items]

            items.toString
          }
        }
      }
    }
  }
}