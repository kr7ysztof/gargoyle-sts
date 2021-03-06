package com.ing.wbaa.gargoyle.sts.service.db.dao

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.sts.config.{GargoyleMariaDBSettings, GargoyleStsSettings}
import com.ing.wbaa.gargoyle.sts.data.UserName
import com.ing.wbaa.gargoyle.sts.data.aws.{AwsAccessKey, AwsCredential}
import com.ing.wbaa.gargoyle.sts.service.TokenGeneration
import com.ing.wbaa.gargoyle.sts.service.db.MariaDb
import org.scalatest.AsyncWordSpec

import scala.util.Random

class STSUserDAOItTest extends AsyncWordSpec with STSUserDAO with MariaDb with TokenGeneration {
  val testSystem: ActorSystem = ActorSystem.create("test-system")

  override protected[this] def stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

  override protected[this] def gargoyleMariaDBSettings: GargoyleMariaDBSettings = GargoyleMariaDBSettings(testSystem)

  private class TestObject {
    val cred: AwsCredential = generateAwsCredential
    val userName: UserName = UserName(Random.alphanumeric.take(32).mkString)
  }

  "STS User DAO" should {
    "insert AwsCredentials with User" that {
      "are new in the db and have a unique accesskey" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, false).map(r => assert(r))
        getAwsCredential(testObject.userName).map(c => assert(c.contains(testObject.cred)))
        getUserSecretKeyAndIsNPA(testObject.cred.accessKey).map(c => assert(c.contains((testObject.userName, testObject.cred.secretKey, false))))
      }

      "user is already present in the db" in {
        val testObject = new TestObject
        val newCred = generateAwsCredential

        insertAwsCredentials(testObject.userName, testObject.cred, false).flatMap { inserted =>
          getAwsCredential(testObject.userName).map { c =>
            assert(c.contains(testObject.cred))
            assert(inserted)
          }
        }

        insertAwsCredentials(testObject.userName, newCred, false).flatMap(inserted =>
          getAwsCredential(testObject.userName).map { c =>
            assert(c.contains(testObject.cred))
            assert(!inserted)
          }
        )
      }

      "have an already existing accesskey" in {
        val testObject = new TestObject

        insertAwsCredentials(testObject.userName, testObject.cred, false).flatMap { inserted =>
          getAwsCredential(testObject.userName).map { c =>
            assert(c.contains(testObject.cred))
            assert(inserted)
          }
        }

        val anotherTestObject = new TestObject
        insertAwsCredentials(anotherTestObject.userName, testObject.cred, false).flatMap(inserted =>
          getAwsCredential(anotherTestObject.userName).map { c =>
            assert(c.isEmpty)
            assert(!inserted)
          }
        )
      }
    }

    "get User, Secret and isNPA" that {
      "exists" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, false)
        getUserSecretKeyAndIsNPA(testObject.cred.accessKey).map { o =>
          assert(o.isDefined)
          assert(o.get._1 == testObject.userName)
          assert(o.get._2 == testObject.cred.secretKey)
          assert(!o.get._3)
        }
      }

      "doesn't exist" in {
        getUserSecretKeyAndIsNPA(AwsAccessKey("DOESNTEXIST")).map { o =>
          assert(o.isEmpty)
        }
      }
    }

    "get AwsCredential" that {
      "exists" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, false)
        getAwsCredential(testObject.userName).map { o =>
          assert(o.isDefined)
          assert(o.get.accessKey == testObject.cred.accessKey)
          assert(o.get.secretKey == testObject.cred.secretKey)
        }
      }

      "doesn't exist" in {
        getAwsCredential(UserName("DOESNTEXIST")).map { o =>
          assert(o.isEmpty)
        }
      }
    }

    "verify duplicate entry" that {
      "username is different and access key is the same" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, false)

        doesUsernameNotExistAndAccessKeyExist(testObjectVerification.userName, testObject.cred.accessKey).map(r => assert(r))
      }

      "username is different and access key is different" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, false)

        doesUsernameNotExistAndAccessKeyExist(testObjectVerification.userName, testObjectVerification.cred.accessKey)
          .map(r => assert(!r))
      }

      "username is same and access key is different" in {
        val testObject = new TestObject
        val testObjectVerification = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, false)

        doesUsernameNotExistAndAccessKeyExist(testObject.userName, testObjectVerification.cred.accessKey)
          .map(r => assert(!r))
      }

      "username is same and access key is same" in {
        val testObject = new TestObject
        insertAwsCredentials(testObject.userName, testObject.cred, false)

        doesUsernameNotExistAndAccessKeyExist(testObject.userName, testObject.cred.accessKey)
          .map(r => assert(!r))
      }
    }
  }
}
