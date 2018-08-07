package com.ing.wbaa.gargoyle.sts
import java.time.Instant

import scala.concurrent.duration.FiniteDuration

package object service {
  private[service] def deadline(duration: FiniteDuration): Instant = {
    import scala.compat.java8.DurationConverters._
    Instant.now().plus(duration.toJava)
  }
}
