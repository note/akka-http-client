package net.michalsitko.scala

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.{ClientTransport, Http}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import net.michalsitko.scala.utils.{Config, ResultLogger}

import scala.util.{Failure, Success}

object ConnectionLevel extends AnyRef with ResultLogger {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val proxyHost = "localhost"
    val proxyPort = 8888

    val proxySettings = new InetSocketAddress(proxyHost, proxyPort)
    val transport = ClientTransport.proxy(None, proxySettings, ClientConnectionSettings(system))

    val connectionFlow =
      Http().outgoingConnectionUsingTransport("akka.io", 443, transport, Http().defaultClientHttpsContext)

    val responseF = Source.single(HttpRequest(uri = "/"))
      .via(connectionFlow)
      .runWith(Sink.head)

    responseF.andThen {
      case Success(_) => println("request succeeded")
      case Failure(_) => println("request failed")
    }.andThen {
      case _ =>
        Http().shutdownAllConnectionPools()
        system.terminate()
    }
  }
}
