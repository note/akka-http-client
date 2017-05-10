package net.michalsitko.scala.utils

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait ResultLogger {
  def logResult(responseF: Future[HttpResponse])(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] = {
    val responseAndEntity = responseF.flatMap { response =>
      response.entity.toStrict(3 seconds).map(entity => (response, entity))
    }

    responseAndEntity
      .map {
        case (response, entity) =>
          logSuccess(response, entity.data.utf8String)
          ()
      }
      .recover {
        case ex =>
          logFailure(ex)
          ()
      }
  }

  private def logSuccess(response: HttpResponse, entity: String) = {
    println()
    println(s"Http Request succeeded with: $response")
    println()
    println(s"Entity: ${entity.take(500)}")
  }

  private def logFailure(failure: Throwable): Unit = {
    println()
    println(s"Http Request failed: $failure")
  }

}
