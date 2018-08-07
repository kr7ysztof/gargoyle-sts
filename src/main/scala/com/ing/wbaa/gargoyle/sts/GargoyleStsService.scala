package com.ing.wbaa.gargoyle.sts

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.ing.wbaa.gargoyle.sts.api.UserApi
import com.ing.wbaa.gargoyle.sts.config.GargoyleHttpSettings
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifierImpl
import com.ing.wbaa.gargoyle.sts.service.{ StsService, TokenServiceImpl, UserServiceImpl }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class GargoyleStsService private[GargoyleStsService] (httpSettings: GargoyleHttpSettings)(implicit system: ActorSystem)
  extends LazyLogging
  with UserApi with UserServiceImpl {

  private[this] implicit val executionContext: ExecutionContext = system.dispatcher

  // The routes we serve
  final val allRoutes: Route = cors() {
    userRoutes ~
      new StsService(new OAuth2TokenVerifierImpl(), new TokenServiceImpl()).stsApiRoutes
  }

  // Details about the server binding.
  final val bind: Future[Http.ServerBinding] = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

    Http(system).bindAndHandle(allRoutes, httpSettings.httpBind, httpSettings.httpPort)
      .andThen {
        case Success(binding) => logger.info(s"Sts service started listening: ${binding.localAddress}")
        case Failure(reason)  => logger.error("Sts service failed to start.", reason)
      }
  }

  def shutdown(): Future[Done] = {
    bind.flatMap(_.unbind)
      .andThen {
        case Success(_)      => logger.info("Sts service stopped.")
        case Failure(reason) => logger.error("Sts service failed to stop.", reason)
      }
  }
}

object GargoyleStsService {
  def apply()(implicit system: ActorSystem): GargoyleStsService = apply(None)

  def apply(httpSettings: GargoyleHttpSettings)(implicit system: ActorSystem): GargoyleStsService =
    apply(httpSettings = Some(httpSettings))

  private[this] def apply(httpSettings: Option[GargoyleHttpSettings])(implicit system: ActorSystem): GargoyleStsService = {
    val gargoyleHttpSettings = httpSettings.getOrElse(GargoyleHttpSettings(system))
    new GargoyleStsService(gargoyleHttpSettings)
  }
}
