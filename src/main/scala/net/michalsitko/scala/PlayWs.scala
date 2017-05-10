package net.michalsitko.scala

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.Future

object PlayWs {
  import scala.concurrent.ExecutionContext.Implicits._

  def main(args: Array[String]): Unit = {
    // Create Akka system for thread and streaming management
    implicit val system = ActorSystem()
    system.registerOnTermination {
      System.exit(0)
    }
    implicit val materializer = ActorMaterializer()

    // Create the standalone WS client
    // no argument defaults to a AhcWSClientConfig created from
    // "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"
    implicit val wsClient = StandaloneAhcWSClient()

    val done = for {
      _ <- singleRequest("https://github.com/akka/akka")
      _ <- singleRequest("https://twitter.com")
      _ <- singleRequest("https://www.scala-lang.org/resources/css/prettify.css")
      _ <- singleRequest("https://www.scala-lang.org/")
    } yield ()


    done
      .andThen { case _ =>
        println("here1")
        wsClient.close()
      }
      .andThen {
        case _ =>
          println("here1")
          system.terminate()
      }
  }

  def singleRequest(url: String)(implicit wsClient: StandaloneWSClient): Future[Unit] = {
    wsClient.url(url).get().map { response ⇒
      val status = response.status
      println(s"Got a response $status")
    }
  }
}
