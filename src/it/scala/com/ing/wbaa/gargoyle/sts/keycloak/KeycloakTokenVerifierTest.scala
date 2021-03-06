package com.ing.wbaa.gargoyle.sts.keycloak

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.ing.wbaa.gargoyle.sts.config.GargoyleKeycloakSettings
import com.ing.wbaa.gargoyle.sts.data.{BearerToken, UserGroup, UserName}
import com.ing.wbaa.gargoyle.sts.helper.{KeycloackToken, OAuth2TokenRequest}
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.{ExecutionContextExecutor, Future}

class KeycloakTokenVerifierTest extends AsyncWordSpec with DiagrammedAssertions with OAuth2TokenRequest with KeycloakTokenVerifier {

  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()(testSystem)
  override implicit val exContext: ExecutionContextExecutor = testSystem.dispatcher

  override val keycloakSettings: GargoyleKeycloakSettings = new GargoyleKeycloakSettings(testSystem.settings.config) {
    override val realmPublicKeyId: String = "FJ86GcF3jTbNLOco4NvZkUCIUmfYCqoqtOQeMfbhNlE"
  }

  private def withOAuth2TokenRequest(formData: Map[String, String])(testCode: KeycloackToken => Assertion): Future[Assertion] = {
    keycloackToken(formData).map(testCode)
  }

  private val validCredentials = Map("grant_type" -> "password", "username" -> "userone", "password" -> "password", "client_id" -> "sts-gargoyle")

  "Keycloak verifier" should {
    "return verified token" in withOAuth2TokenRequest(validCredentials) { keycloakToken =>
      val token = verifyAuthenticationToken(BearerToken(keycloakToken.access_token))
      assert(token.map(_.userName).contains(UserName("userone")))
      assert(token.exists(_.userGroups.contains(UserGroup("user"))))
    }
  }

  "return None when an invalid token is provided" in {
    val result = verifyAuthenticationToken(BearerToken("invalid"))
    assert(result.isEmpty)
  }
}
