package net.michalsitko

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.{ClientTransport, Http}
import akka.stream.ActorMaterializer
import net.michalsitko.utils.ResultLogger

import scala.concurrent.Future

object RequestLevel extends AnyRef with ResultLogger {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val proxySettings = new InetSocketAddress("localhost", 8888)
    val transport = ClientTransport.proxy(None, proxySettings, ClientConnectionSettings(system))
//    val transport = ClientTransport.TCP(None, ClientConnectionSettings(system))

    val connectionPoolSettings = ConnectionPoolSettings(system).withTransport(transport)

    def singleRequest(uri: String): Future[Unit] = {
      val responseF = Http().singleRequest(HttpRequest(uri = uri), Http().defaultClientHttpsContext, connectionPoolSettings)
      logResult(responseF)
    }

    val done = for {
      _ <- singleRequest("https://github.com/akka/akka")
      _ <- singleRequest("https://twitter.com")
      _ <- singleRequest("https://www.scala-lang.org/resources/css/prettify.css")
      _ <- singleRequest("https://www.scala-lang.org/xyzxyz")
    } yield ()

    done.andThen {
      case _ =>
        Http().shutdownAllConnectionPools()
        materializer.shutdown()
        system.terminate()
    }
  }

}
