package com.ing.wbaa.gargoyle.sts.service

import java.util.concurrent.TimeUnit

import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration.FiniteDuration

class TokenServiceTest extends WordSpec with Matchers {

  val tokenService = new TokenServiceImpl

  //TODO it is a mock test
  "Token service" should {
    "get an assume role" in {
      tokenService.getAssumeRoleWithWebIdentity("arn", "roleSession", "token", FiniteDuration(100, TimeUnit.SECONDS)).get.toString.isEmpty shouldBe false
    }

    "get a session token" in {
      tokenService.getSessionToken(FiniteDuration(1000, TimeUnit.SECONDS)).get.toString.isEmpty shouldBe false
    }

  }

}
