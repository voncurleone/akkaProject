package com.iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import Device._
  import DeviceManager._

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

  "Device group" must {
    "be able to register a device actor" in {

      //setup device group and probe
      val probe = createTestProbe[DeviceRegistered]()
      val deviceGroup = spawn(DeviceGroup("group:1"))

      //register the probe as the replyTo
      deviceGroup ! RequestTrackDevice("group:1", "device:1", probe.ref)
      val registered1 = probe.receiveMessage()
      val device1 = registered1.device

      //register a second deviceID with the probe
      deviceGroup ! RequestTrackDevice("group:1", "device:2", probe.ref)
      val registered2 = probe.receiveMessage()
      val device2 = registered2.device
      device1 should !== (device2)

      //check that the device actors are working
      val recordProbe = createTestProbe[TempRecorded]()
      device1 ! RecordTemp(0, 5.5, recordProbe.ref)
      recordProbe.expectMessage(TempRecorded(0))
      device2 ! RecordTemp(1, -.3, recordProbe.ref)
      recordProbe.expectMessage(TempRecorded(1))
    }

    "ignore request with incorrect groupId" in {
      val probe = createTestProbe[DeviceRegistered]()
      val deviceGroup = spawn(DeviceGroup("group:2"))

      deviceGroup ! RequestTrackDevice("group:0", "device:42", probe.ref)
      probe.expectNoMessage(1.second)
    }

    "device Id will always returns the same actor" in {
      val probe = createTestProbe[DeviceRegistered]()
      val deviceGroup = spawn(DeviceGroup("group:1"))

      deviceGroup ! RequestTrackDevice("group:1", "device:1", probe.ref)
      val deviceOne = probe.receiveMessage().device

      deviceGroup ! RequestTrackDevice("group:1", "device:1", probe.ref)
      val deviceTwo = probe.receiveMessage().device

      deviceOne should === (deviceTwo)
    }

    "be able to list active devices in the group" in {
      val registerProbe = createTestProbe[DeviceRegistered]()
      val group = spawn(DeviceGroup("group:0"))

      group ! RequestTrackDevice("group:0", "device:0", registerProbe.ref)
      registerProbe.receiveMessage()

      group ! RequestTrackDevice("group:0", "device:1", registerProbe.ref)
      registerProbe.receiveMessage()

      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      group ! RequestDeviceList(0, "group:0", deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(0, Set("device:1", "device:0")))
    }

    "be able to list active devices after one has shut down" in {
      val registerProbe = createTestProbe[DeviceRegistered]()
      val group = spawn(DeviceGroup("group:0"))

      group ! RequestTrackDevice("group:0", "device:0", registerProbe.ref)
      val toShutdown = registerProbe.receiveMessage().device

      group ! RequestTrackDevice("group:0", "device:1", registerProbe.ref)
      registerProbe.receiveMessage()

      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      group ! RequestDeviceList(0, "group:0", deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(0, Set("device:1", "device:0")))

      toShutdown ! Passivate
      registerProbe.expectTerminated(toShutdown, registerProbe.remainingOrDefault)

      registerProbe.awaitAssert {
        group ! RequestDeviceList(  1, "group:0", deviceListProbe.ref)
        deviceListProbe.expectMessage(ReplyDeviceList(1, Set("device:1")))
      }
    }
  }

  "Device Manager" must {
    //todo write tests for device manager. They should be similar to Device group
  }
}
