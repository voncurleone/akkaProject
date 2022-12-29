package com.iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
class DeviceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import Device._

  "Device actor" must {
    "reply with empty reading if no temp is known" in {
      val probe = createTestProbe[RespondTemp]()
      val device = spawn(Device("group:0", "device:0"))

      device ! ReadTemp(0, probe.ref)
      val response = probe.receiveMessage()
      response.requestId should === (0)
      response.value should === (None)
    }
  }
}
