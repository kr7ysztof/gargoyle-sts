package com.ing.wbaa.gargoyle.sts.oauth

import java.time.Instant

import scala.concurrent.{ ExecutionContext, Future }

case class VerifiedToken(
    token: String,
    id: String,
    name: String,
    username: String,
    email: String,
    roles: Seq[String],
    expiration: Instant)

trait OAuth2TokenVerifier {
  def verifyToken(token: BearerToken): Future[VerifiedToken]
}

/**
 * Test implementation of OAuth2 token verifier
 */
class OAuth2TokenVerifierImpl(implicit executionContext: ExecutionContext) extends OAuth2TokenVerifier {

  override def verifyToken(token: BearerToken): Future[VerifiedToken] = {
    if ("validToken".equals(token.value)) Future[VerifiedToken](VerifiedToken(token.value, "id", "name", "username", "email", Seq.empty, Instant.EPOCH))
    else Future.failed(new Exception("invalid token"))
  }
}
