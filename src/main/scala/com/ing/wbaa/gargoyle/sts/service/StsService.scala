package com.ing.wbaa.gargoyle.sts
package service

import api.StsApi
import oauth.OAuth2TokenVerifier

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

class StsService(val oAuth2TokenVerifier: OAuth2TokenVerifier, tokenService: TokenService)(implicit executionContext: ExecutionContext) extends StsApi {
  override protected[this] def getAssumeRoleWithWebIdentity(roleArn: String, roleSessionName: String, webIdentityToken: String, sessionDuration: FiniteDuration): Future[Option[AssumeRoleWithWebIdentityResponse]] = {
    Future {
      tokenService.getAssumeRoleWithWebIdentity(roleArn, roleSessionName, webIdentityToken, sessionDuration)
    }
  }
  override protected[this] def getSessionToken(sessionDuration: FiniteDuration): Future[Option[GetSessionTokenResponse]] = {
    Future {
      tokenService.getSessionToken(sessionDuration)
    }
  }
}
