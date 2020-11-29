package EShop.lab5

import java.net.URI

import EShop.lab5.ProductCatalog.{GetItems, Item, Items}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsArray, JsString, JsValue, JsonFormat, enrichAny}
import scala.concurrent.Await

import scala.language.postfixOps


trait JsonSupport1 extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val uriFormat: JsonFormat[URI] = new JsonFormat[java.net.URI] {
    override def write(uri: java.net.URI): spray.json.JsValue = JsString(uri.toString)
    override def read(json: JsValue): URI = json match {
      case JsString(url) => new URI(url)
      case _             => throw new RuntimeException("Parse exception")
    }
  }

  implicit val itemFormat: JsonFormat[Item] = jsonFormat5(Item)

  implicit val itemsFormat: JsonFormat[Items] = new JsonFormat[Items] {
    override def read(json: JsValue): Items = Items(json.convertTo[List[Item]])

    override def write(items: Items): JsValue = JsArray(items.items.map(_.toJson).toVector)
  }

}

object ProductCatalogServer extends App {
  new ProductCatalogServer().startServer("localhost", 9000)
}

class ProductCatalogServer extends HttpApp with JsonSupport1 {

  override protected def routes: Route = {
    path("/") {
      parameter('brand.as[String], 'productKeyWords.repeated) { (brand, productKeyWords) =>
        get {
          extractExecutionContext{implicit executionContext =>
            extractActorSystem { actorSystem =>
              val productCatalog = actorSystem.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2554/user/productcatalog")
              implicit val timeout: Timeout = Timeout(5 seconds)
              val future = productCatalog ? GetItems(brand, productKeyWords.toSeq.toList)

              val result = Await.result(future.mapTo[Items], timeout.duration)
              result.toJson.prettyPrint
              complete {
                  result.toJson
              }
            }
          }

        }
      }
    }
  }

}