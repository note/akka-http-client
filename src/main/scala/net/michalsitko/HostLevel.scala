package net.michalsitko

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.{ClientTransport, Http}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import net.michalsitko.utils.ResultLogger

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object HostLevel extends AnyRef with ResultLogger {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val source: Source[(HttpRequest, String), NotUsed] = Source(List("/")).map(path => (HttpRequest(uri = path), path))

    val proxySettings = new InetSocketAddress("localhost", 8888)
    val transport = ClientTransport.proxy(None, proxySettings, ClientConnectionSettings(system))

    val connectionPoolSettings = ConnectionPoolSettings(system).withTransport(transport)
    val poolClientFlow: Flow[(HttpRequest, String), (Try[HttpResponse], String), HostConnectionPool] =
      Http().cachedHostConnectionPool[String]("www.scala-lang.org", 80, connectionPoolSettings)

    val result: Future[(Try[HttpResponse], String)] = source.via(poolClientFlow).runWith(Sink.head)

    result
      .flatMap {
        case (Success(response), _) =>
          logResult(Future.successful(response))
        case (Failure(ex), _) =>
          Future.successful(())
      }
      .andThen {
        case _ =>
          Http().shutdownAllConnectionPools()
          materializer.shutdown()
          system.terminate()
      }
  }
}
