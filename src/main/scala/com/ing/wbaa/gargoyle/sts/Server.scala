package com.ing.wbaa.gargoyle.sts

import akka.actor.ActorSystem

object Server extends App {
  val stsService: GargoyleStsService = GargoyleStsService()(ActorSystem.create("gargoyle-sts"))
}
