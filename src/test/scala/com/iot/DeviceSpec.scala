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

    "reply with latest temp reading" in {
      //set up device and probe
      val recordProbe = createTestProbe[TempRecorded]()
      val readProbe = createTestProbe[RespondTemp]()
      val device = spawn(Device("grouo:1", "Device:1"))

      //send it a record temp message
      device ! RecordTemp(1, -1.1, recordProbe.ref)
      recordProbe.expectMessage(TempRecorded(1))

      //read the value
      device ! ReadTemp(2, readProbe.ref)
      val readResponse1 = readProbe.receiveMessage()
      readResponse1.requestId should === (2)
      readResponse1.value should === (Some(-1.1))

      //send it a second record temp message
      device ! RecordTemp(3, 1.1, recordProbe.ref)
      recordProbe.expectMessage(TempRecorded(3))

      //read the value
      device ! ReadTemp(4, readProbe.ref)
      val readResponse2 = readProbe.receiveMessage()
      readResponse2.requestId should ===(4)
      readResponse2.value should ===(Some(1.1))
    }
  }
}
