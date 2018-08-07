package com.ing.wbaa.gargoyle.sts.service
import java.time.format.DateTimeFormatter

import scala.concurrent.duration.FiniteDuration
import scala.xml.Elem

sealed trait XmlResponse

final case class AssumeRoleWithWebIdentityResponse(sessionDuration: FiniteDuration) {
  // Stubbed implementation
  def asXml: Elem =
    <AssumeRoleWithWebIdentityResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
      <AssumeRoleWithWebIdentityResult>
        <SubjectFromWebIdentityToken>amzn1.account.AF6RHO7KZU5XRVQJGXK6HB56KR2A</SubjectFromWebIdentityToken>
        <Audience>client.5498841531868486423.1548@apps.example.com</Audience>
        <AssumedRoleUser>
          <Arn>arn:aws:sts::123456789012:assumed-role/FederatedWebIdentityRole/app1</Arn>
          <AssumedRoleId>AROACLKWSDQRAOEXAMPLE:app1</AssumedRoleId>
        </AssumedRoleUser>
        <Credentials>
          <SessionToken>okSessionToken</SessionToken>
          <SecretAccessKey>secretKey</SecretAccessKey>
          <Expiration>{ DateTimeFormatter.ISO_INSTANT.format(deadline(sessionDuration)) }</Expiration>
          <AccessKeyId>okAccessKey</AccessKeyId>
        </Credentials>
        <Provider>www.amazon.com</Provider>
      </AssumeRoleWithWebIdentityResult>
      <ResponseMetadata>
        <RequestId>ad4156e9-bce1-11e2-82e6-6b6efEXAMPLE</RequestId>
      </ResponseMetadata>
    </AssumeRoleWithWebIdentityResponse>
}

final case class GetSessionTokenResponse(sessionDuration: FiniteDuration) {
  // Stubbed implementation.
  def asXml: Elem =
    <GetSessionTokenResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
      <GetSessionTokenResult>
        <Credentials>
          <SessionToken>okSessionToken</SessionToken>
          <SecretAccessKey>secretKey</SecretAccessKey>
          <Expiration>{ DateTimeFormatter.ISO_INSTANT.format(deadline(sessionDuration)) }</Expiration>
          <AccessKeyId>okAccessKey</AccessKeyId>
        </Credentials>
      </GetSessionTokenResult>
      <ResponseMetadata>
        <RequestId>58c5dbae-abef-11e0-8cfe-getAssumeRoleWithWebIdentity09039844ac7d</RequestId>
      </ResponseMetadata>
    </GetSessionTokenResponse>
}

trait TokenService {
  def getAssumeRoleWithWebIdentity(roleArn: String, roleSessionName: String, webIdentityToken: String, sessionDuration: FiniteDuration): Option[AssumeRoleWithWebIdentityResponse]

  def getSessionToken(sessionDuration: FiniteDuration): Option[GetSessionTokenResponse]
}

/**
 * Simple s3 token service implementation for test
 */
class TokenServiceImpl extends TokenService {
  override def getAssumeRoleWithWebIdentity(roleArn: String, roleSessionName: String, webIdentityToken: String, sessionDuration: FiniteDuration): Option[AssumeRoleWithWebIdentityResponse] =
    Some(AssumeRoleWithWebIdentityResponse(sessionDuration))

  override def getSessionToken(sessionDuration: FiniteDuration): Option[GetSessionTokenResponse] =
    Some(GetSessionTokenResponse(sessionDuration))
}

