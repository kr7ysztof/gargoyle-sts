package com.ing.wbaa.gargoyle.sts.api

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.{FormData, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.{AuthorizationFailedRejection, MissingFormFieldRejection, MissingQueryParamRejection, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.gargoyle.sts.oauth.{BearerToken, OAuth2TokenVerifier, VerifiedToken}
import com.ing.wbaa.gargoyle.sts.service.{AssumeRoleWithWebIdentityResponse, GetSessionTokenResponse}
import org.scalatest.{DiagrammedAssertions, Matchers, WordSpec}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class StsApiSpec extends WordSpec with Matchers with DiagrammedAssertions with ScalatestRouteTest {

  class MockStsApi extends StsApi {
    override def getAssumeRoleWithWebIdentity(roleArn: String, roleSessionName: String, webIdentityToken: String, sessionDuration: FiniteDuration): Future[Option[AssumeRoleWithWebIdentityResponse]] = ???
    override def getSessionToken(sessionDuration: FiniteDuration): Future[Option[GetSessionTokenResponse]] = ???
    def verifyToken(token: BearerToken): Future[VerifiedToken] = ???
    override protected[this] val oAuth2TokenVerifier: OAuth2TokenVerifier = new OAuth2TokenVerifier {
      override def verifyToken(token: BearerToken): Future[VerifiedToken] = MockStsApi.this.verifyToken(token)
    }
  }

  val validOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer valid")
  val validOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "valid"))
  val invalidOAuth2TokenHeader: RequestTransformer = addHeader("Authorization", "Bearer invalid")
  val invalidOAuth2TokenCookie: RequestTransformer = addHeader(Cookie("X-Authorization-Token", "invalid"))

  val actionAssumeRoleWithWebIdentity = "?Action=AssumeRoleWithWebIdentity"
  val actionGetSessionToken = "?Action=GetSessionToken"
  val durationQuery = "&DurationSeconds=3600"
  val roleNameSessionQuery = "&RoleSessionName=app1"
  val arnQuery = "&RoleArn=arn:aws:iam::123456789012:role/FederatedWebIdentityRole"
  val webIdentityTokenQuery = "&WebIdentityToken=Atza%7CIQ"
  val providerIdQuery = "&ProviderId=testRrovider.com"
  val tokenCodeQuery = "&TokenCode=sdfdsfgg"

  "STS api the GET method" should {
    "return rejection because missing the Action parameter" in new MockStsApi {
      Get("/") ~> stsApiRoutes ~> check {
        rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("Action"))
      }
    }

    "reject unknown actions" in new MockStsApi {
      Get("/?Action=unknownAction") ~> stsApiRoutes ~> check {
        assert(rejections.contains(UnknownAction("unknownAction")))
      }
    }

    "return BadRequest if the action in unknown" in new MockStsApi {
      Get("/?Action=unknownAction") ~> Route.seal(stsApiRoutes) ~> check {
        assert(status == StatusCodes.BadRequest)
      }
    }

    // XXX: This is a bad test:
    //  - We should test that we forbid if the token doesn't verify.
    //  - When testing verification (elsewhere) we should check the token doesn't verify if the session duration is too large.
    "return forbidden because the DurationSeconds parameter is to big" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery&DurationSeconds=1000000000") ~>
        validOAuth2TokenHeader ~> Route.seal(stsApiRoutes) ~> check {
          assert(status == StatusCodes.Forbidden)
        }
    }

    "return rejection because missing the RoleSessionName parameter" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$arnQuery$webIdentityTokenQuery") ~>
        stsApiRoutes ~> check {
          rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("RoleSessionName"))
        }
    }

    "return rejection because missing the WebIdentityToken parameter" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery") ~> stsApiRoutes ~> check {
        rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("WebIdentityToken"))
      }
    }

    "return rejection because missing the RoleArn parameter" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$roleNameSessionQuery$webIdentityTokenQuery") ~> stsApiRoutes ~> check {
        rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("RoleArn"))
      }
    }

    "return an assume role" in new MockStsApi {

      override def getAssumeRoleWithWebIdentity(roleArn: String, roleSessionName: String, webIdentityToken: String, sessionDuration: FiniteDuration): Future[Option[AssumeRoleWithWebIdentityResponse]] = {
        Future.successful(Some(AssumeRoleWithWebIdentityResponse(FiniteDuration(10, TimeUnit.MINUTES))))
      }
      override def verifyToken(token: BearerToken): Future[VerifiedToken] = {
        Future.successful(VerifiedToken("aToken", "anId", "aName", "aUsername", "anEmail", Seq.empty, Instant.MAX))
      }

      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenHeader ~> stsApiRoutes ~> check {
          // XXX: Should this be application/xml ?
          assert(mediaType == MediaTypes.`text/xml`)
          assert(status == StatusCodes.OK)
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the header" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        invalidOAuth2TokenHeader ~> stsApiRoutes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the cookie" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        invalidOAuth2TokenCookie ~> stsApiRoutes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid credential in the WebIdentityToken param" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$roleNameSessionQuery$arnQuery$roleNameSessionQuery$webIdentityTokenQuery") ~>
        stsApiRoutes ~> check {
          rejection shouldEqual AuthorizationFailedRejection
        }
    }

    "return an assume role because valid credential are in the WebIdentityToken param" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$durationQuery$providerIdQuery$roleNameSessionQuery$arnQuery&WebIdentityToken=valid") ~>
        stsApiRoutes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return an assume role because valid credential are in the cookie" in new MockStsApi {
      Get(s"/$actionAssumeRoleWithWebIdentity$providerIdQuery$roleNameSessionQuery$arnQuery$webIdentityTokenQuery") ~>
        validOAuth2TokenCookie ~> stsApiRoutes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return a session token because valid credential in the header" in new MockStsApi {
      Get(s"/$actionGetSessionToken") ~> validOAuth2TokenHeader ~> stsApiRoutes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token because valid credential in the cookie" in new MockStsApi {
      Get(s"/$actionGetSessionToken") ~> validOAuth2TokenCookie ~> stsApiRoutes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return forbidden because the DurationSeconds is to big" in new MockStsApi {
      Get(s"/$actionGetSessionToken&DurationSeconds=1000000000") ~> validOAuth2TokenHeader ~> stsApiRoutes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "for action GetSessionToken return rejection because invalid authentication in the cookie" in new MockStsApi {
      Get(s"/$actionGetSessionToken&DurationSeconds=1000") ~> invalidOAuth2TokenCookie ~> stsApiRoutes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
      }
    }

    "for action GetSessionToken return rejection because bad authentication in the header" in new MockStsApi {
      Get(s"/$actionGetSessionToken&DurationSeconds=1000") ~> invalidOAuth2TokenHeader ~> stsApiRoutes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
      }
    }

    "for action GetSessionToken return rejection because no authentication token" in new MockStsApi {
      Get(s"/$actionGetSessionToken&DurationSeconds=1000") ~> stsApiRoutes ~> check {
        rejection shouldEqual AuthorizationFailedRejection
      }
    }
  }

  def queryToFormData(queries: String*): Map[String, String] = {
    queries.map(_.substring(1).split("="))
      .map {
        case Array(k, v) => (k, v)
      }.toMap
  }

  "STS api the POST method" should {
    "return rejection because missing the Action parameter" in new MockStsApi {
      Post("/") ~> stsApiRoutes ~> check {
        rejections should contain atLeastOneElementOf List(MissingQueryParamRejection("Action"))
      }
    }

    "reject unknown actions" in new MockStsApi {
      Post("/", FormData("Action" -> "unknownAction")) ~> stsApiRoutes ~> check {
        assert(rejections.contains(UnknownAction("unknownAction")))
      }
    }

    "return BadRequest if the action in unknown" in new MockStsApi {
      Post("/", FormData("Action" -> "unknownAction")) ~> Route.seal(stsApiRoutes) ~> check {
        assert(status == StatusCodes.BadRequest)
      }
    }

    "return a bad request because the action in unknown" in new MockStsApi {
      Post("/", FormData("Action" -> "unknownAction")) ~> stsApiRoutes ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "return forbidden because the DurationSeconds parameter is to big" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery) + ("DurationSeconds" -> "1000000000"))) ~>
        validOAuth2TokenHeader ~> stsApiRoutes ~> check {
          status shouldEqual StatusCodes.Forbidden
        }
    }

    "return rejection because missing the RoleSessionName parameter" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, providerIdQuery, durationQuery, arnQuery, webIdentityTokenQuery))) ~>
        stsApiRoutes ~> check {
          rejections should contain atLeastOneElementOf List(MissingFormFieldRejection("RoleSessionName"))
        }
    }

    "return rejection because missing the WebIdentityToken parameter" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery))) ~>
        stsApiRoutes ~> check {
          rejections should contain atLeastOneElementOf List(MissingFormFieldRejection("WebIdentityToken"))
        }
    }

    "return rejection because missing the RoleArn parameter" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        stsApiRoutes ~> check {
          rejections should contain atLeastOneElementOf List(MissingFormFieldRejection("RoleArn"))
        }
    }

    "return an assume role" in new MockStsApi {

      override def getAssumeRoleWithWebIdentity(roleArn: String, roleSessionName: String, webIdentityToken: String, sessionDuration: FiniteDuration): Future[Option[AssumeRoleWithWebIdentityResponse]] = {
        Future.successful(Some(AssumeRoleWithWebIdentityResponse(FiniteDuration(10, TimeUnit.MINUTES))))
      }
      override def verifyToken(token: BearerToken): Future[VerifiedToken] = {
        Future.successful(VerifiedToken("aToken", "anId", "aName", "aUsername", "anEmail", Seq.empty, Instant.MAX))
      }

      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        validOAuth2TokenHeader ~>
        stsApiRoutes ~> check {
          // XXX: Should this be application/xml ?
          assert(mediaType == MediaTypes.`text/xml`)
          assert(status == StatusCodes.OK)
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the header" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        invalidOAuth2TokenHeader ~> stsApiRoutes ~> check {
          rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid authentication in the cookie" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        invalidOAuth2TokenCookie ~> stsApiRoutes ~> check {
          rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
        }
    }

    "for action AssumeRoleWithWebIdentity return rejection because invalid credential in the WebIdentityToken param" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        stsApiRoutes ~> check {
          rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
        }
    }

    "return an assume role because valid credential are in the WebIdentityToken param" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery) + ("WebIdentityToken" -> "valid"))) ~>
        stsApiRoutes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return an assume role because valid credential are in the cookie" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionAssumeRoleWithWebIdentity, roleNameSessionQuery,
        arnQuery, providerIdQuery, roleNameSessionQuery, webIdentityTokenQuery))) ~>
        validOAuth2TokenCookie ~> stsApiRoutes ~> check {
          status shouldEqual StatusCodes.OK
        }
    }

    "return a session token because valid credential in the header" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionGetSessionToken))) ~> validOAuth2TokenHeader ~> stsApiRoutes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token because valid credential in the cookie" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionGetSessionToken))) ~> validOAuth2TokenCookie ~> stsApiRoutes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return a session token because valid credential in the TokenCode" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery) + ("TokenCode" -> "valid"))) ~> stsApiRoutes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return forbidden because the DurationSeconds is to big" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionGetSessionToken) + ("DurationSeconds" -> "1000000000"))) ~> validOAuth2TokenHeader ~> stsApiRoutes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "return rejection because the TokenCode is invalid" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionGetSessionToken, tokenCodeQuery, durationQuery))) ~> stsApiRoutes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }

    "for action GetSessionToken return rejection because invalid authentication in the cookie" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery))) ~> invalidOAuth2TokenCookie ~> stsApiRoutes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }

    "for action GetSessionToken return rejection because bad authentication in the header" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery))) ~> invalidOAuth2TokenHeader ~> stsApiRoutes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }

    "for action GetSessionToken return rejection because no authentication token" in new MockStsApi {
      Post("/", FormData(queryToFormData(actionGetSessionToken, durationQuery))) ~> stsApiRoutes ~> check {
        rejections should contain atLeastOneElementOf List(AuthorizationFailedRejection)
      }
    }
  }
}

