package net.michalsitko

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.{ClientTransport, Http}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import net.michalsitko.utils.ResultLogger

object ConnectionLevel extends AnyRef with ResultLogger {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val proxySettings = new InetSocketAddress("localhost", 8888)
    val transport = ClientTransport.proxy(None, proxySettings, ClientConnectionSettings(system))

    val connectionFlow =
      Http().outgoingConnectionUsingTransport("www.scala-lang.org", 443, transport, Http().defaultClientHttpsContext)

    val responseF = Source.single(HttpRequest(uri = "/"))
      .via(connectionFlow)
      .runWith(Sink.head)

    logResult(responseF).andThen {
      case _ =>
        Http().shutdownAllConnectionPools()
        materializer.shutdown()
        system.terminate()
    }
  }
}
