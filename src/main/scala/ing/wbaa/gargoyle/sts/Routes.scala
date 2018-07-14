package ing.wbaa.gargoyle.sts

import akka.http.scaladsl.server.{Route, RouteConcatenation}
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ing.wbaa.gargoyle.sts.api.{STSApi, UserApi}
import ing.wbaa.gargoyle.sts.service.{TokenServiceImpl, UserServiceImpl}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait Routes extends RouteConcatenation {
  this: Actors with CoreActorSystem =>

  val routes: Route = cors() {
    implicit val exContext: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(5.seconds)
    new UserApi(new UserServiceImpl()).routes ~
      new STSApi(new TokenServiceImpl()).routes
  }
}
