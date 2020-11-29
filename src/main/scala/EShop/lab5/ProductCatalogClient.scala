package EShop.lab5

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class ProductCatalogClient extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val http: HttpExt = Http(context.system)

  override def preStart(): Unit = {
    http.singleRequest(HttpRequest(uri = "http://localhost:9000/?brand=gerber&productWithKeys=['cream']"))
      .pipeTo(self)
  }

  override def receive: Receive = {
    case resp@HttpResponse(StatusCodes.OK, headers, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        println("Got response, body: " + body.utf8String)
        resp.discardEntityBytes()
        shutdown()
      }
  }

  def shutdown(): Future[Terminated] = {
    Await.result(http.shutdownAllConnectionPools(),Duration.Inf)
    context.system.terminate()
  }
}

object Main {

  def main(args: Array[String]) {


    val config = ConfigFactory.load()

    val system = ActorSystem("http-system", config.getConfig("eshop").withFallback(config))
    val productCatalogClient = system.actorOf(Props[ProductCatalogClient], "ProductCatalogClient-Actor")

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}