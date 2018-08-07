package com.ing.wbaa.gargoyle.sts.api

import java.util.concurrent.TimeUnit

import akka.http.javadsl.server.CustomRejection
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, Directive1, RejectionHandler, Route }
import akka.http.scaladsl.unmarshalling.{ PredefinedFromStringUnmarshallers, Unmarshaller }
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2Directives.oAuth2Authorization
import com.ing.wbaa.gargoyle.sts.oauth.OAuth2TokenVerifier
import com.ing.wbaa.gargoyle.sts.service.{ AssumeRoleWithWebIdentityResponse, GetSessionTokenResponse }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

trait StsApi extends LazyLogging {
  import StsApi._

  // Enable some implicit conversions.
  import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
  import scala.concurrent.duration._

  protected[this] case class UnknownAction(action: String) extends CustomRejection

  private[this] def stsAction(actions: PartialFunction[String, Route]): Route = {
    (parameter("Action") | formField("Action")) { action =>
      if (actions.isDefinedAt(action)) {
        actions(action)
      } else {
        reject(UnknownAction(action))
      }
    }
  }

  private[this] implicit val durationFromSecondsUnmarshaller: Unmarshaller[String, FiniteDuration] =
    PredefinedFromStringUnmarshallers.longFromStringUnmarshaller.andThen(
      Unmarshaller.strict[Long, FiniteDuration] { value: Long =>
        if (0 > value) {
          throw new IllegalArgumentException(s"'$value' is not a valid duration in seconds; it must be positive.")
        }
        FiniteDuration(value, TimeUnit.SECONDS)
      })

  private[this] val assumeRole: Directive[(String, String, String, FiniteDuration)] =
    (parameters(('RoleArn, 'RoleSessionName, 'WebIdentityToken, 'DurationSeconds.as[FiniteDuration].?)) |
      formFields(('RoleArn, 'RoleSessionName, 'WebIdentityToken, 'DurationSeconds.as[FiniteDuration].?)))
      .tmap(extractions => extractions.copy(_4 = extractions._4.getOrElse(defaultSessionDuration)))

  private[this] val sessionToken: Directive1[FiniteDuration] =
    (parameters('DurationSeconds.as[FiniteDuration].?) |
      formField('DurationSeconds.as[FiniteDuration].?))
      .map(_.getOrElse(defaultSessionDuration))

  protected[this] def getAssumeRoleWithWebIdentity(roleArn: String, roleSessionName: String, webIdentityToken: String, sessionDuration: FiniteDuration): Future[Option[AssumeRoleWithWebIdentityResponse]]
  protected[this] def getSessionToken(sessionDuration: FiniteDuration): Future[Option[GetSessionTokenResponse]]
  protected[this] val oAuth2TokenVerifier: OAuth2TokenVerifier

  implicit def stsRejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle { case UnknownAction(unknownAction: String) =>
      complete((StatusCodes.BadRequest, s"Unknown action: $unknownAction"))
    }
    .result()

  val stsApiRoutes: Route = logRequestResult("debug") {
    pathSingleSlash {
      (get | post) {
        stsAction {
          case "AssumeRoleWithWebIdentity" =>
            assumeRole { (roleArn, roleSessionName, webIdentityToken, durationSeconds) =>
              oAuth2Authorization(oAuth2TokenVerifier) { token =>
                onSuccess(getAssumeRoleWithWebIdentity(roleArn, roleSessionName, webIdentityToken, durationSeconds)) {
                  case Some(assumeRoleWithWebIdentity) => complete(assumeRoleWithWebIdentity.asXml)
                  case _                               => complete(StatusCodes.Forbidden)
                }
              }
            }
          case "GetSessionToken" =>
            sessionToken { durationSeconds =>
              oAuth2Authorization(oAuth2TokenVerifier) { oauthToken =>
                onSuccess(getSessionToken(durationSeconds)) {
                  case Some(token) => complete(token.asXml)
                  case _           => complete(StatusCodes.Forbidden)
                }
              }
            }
        }
      }
    }
  }
}

object StsApi {
  import scala.concurrent.duration._
  private[StsApi] val defaultSessionDuration: FiniteDuration = 1.hour
}
