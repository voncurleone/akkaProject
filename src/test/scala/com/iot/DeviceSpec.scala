package com.iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.SpawnProtocol.Spawn
import com.iot.DeviceGroupQuery.WrappedRespondTemp
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

    "be able to collect all temps from all active devices" in {
      val groupActor = spawn(DeviceGroup("0"))
      val allTempsProbe = createTestProbe[ReplyAllTemps]()
      val registerProbe = createTestProbe[DeviceRegistered]()

      groupActor ! RequestTrackDevice("0", "1", registerProbe.ref)
      val device1 = registerProbe.receiveMessage().device

      groupActor ! RequestTrackDevice("0", "2", registerProbe.ref)
      val device2 = registerProbe.receiveMessage().device

      groupActor ! RequestTrackDevice("0", "3", registerProbe.ref)
      registerProbe.receiveMessage()

      val recordedProbe = createTestProbe[TempRecorded]()
      device1 ! RecordTemp(0, 2.6, recordedProbe.ref)
      recordedProbe.expectMessage(TempRecorded(0))
      device2 ! RecordTemp(1, 6.8, recordedProbe.ref)
      recordedProbe.expectMessage(TempRecorded(1))
      //left out device 3 so we can test the case when a temp is unavailable

      groupActor ! RequestAllTemps(0, "0", allTempsProbe.ref)

      allTempsProbe.expectMessage(
        ReplyAllTemps(
          requestId = 0,
          Map("1" -> Temp(2.6), "2" -> Temp(6.8), "3" -> TempNotAvailable)
        )
      )
    }
  }

  "Device Manager" must {
    "register device actors" in {
      val deviceManager = spawn(DeviceManager())
      val probe = createTestProbe[DeviceRegistered]()

      deviceManager ! RequestTrackDevice("group:0", "device:0", probe.ref)
      val device0 = probe.receiveMessage().device

      deviceManager ! RequestTrackDevice("group:0", "device:1", probe.ref)
      val device1 = probe.receiveMessage().device
      device0 should !== (device1)

      val tempProbe = createTestProbe[TempRecorded]()
      device0 ! RecordTemp(0, 5.5, tempProbe.ref)
      tempProbe.expectMessage(TempRecorded(0))
      device1 ! RecordTemp(1, 3.3, tempProbe.ref)
      tempProbe.expectMessage(TempRecorded(1))
    }

    "return correct device for given Id" in {
      val deviceProbe = createTestProbe[DeviceRegistered]()
      val deviceManager = spawn(DeviceManager())

      deviceManager ! RequestTrackDevice("group:0", "device:0", deviceProbe.ref)
      val device0 = deviceProbe.receiveMessage().device

      deviceManager ! RequestTrackDevice("group:0", "device:0", deviceProbe.ref)
      val device1 = deviceProbe.receiveMessage().device

      device0 should === (device1)
    }

    "list all active actors in a group" in {
      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      val deviceProbe = createTestProbe[DeviceRegistered]()
      val deviceManager = spawn(DeviceManager())

      deviceManager ! RequestTrackDevice("group:0", "device:0", deviceProbe.ref)
      deviceProbe.receiveMessage()

      deviceManager ! RequestTrackDevice("group:0", "device:1", deviceProbe.ref)
      deviceProbe.receiveMessage()

      deviceManager ! RequestTrackDevice("group:1", "device:2", deviceProbe.ref)
      deviceProbe.receiveMessage()

      deviceManager ! RequestDeviceList(0, "group:0", deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(0, Set("device:0", "device:1")))

      deviceManager ! RequestDeviceList(1, "group:1", deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(1, Set("device:2")))
    }

    "list all active actors once one has been shutdown" in {
      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      val deviceProbe = createTestProbe[DeviceRegistered]()
      val deviceManager = spawn(DeviceManager())

      deviceManager ! RequestTrackDevice("group:0", "device:0", deviceProbe.ref)
      deviceProbe.receiveMessage()

      deviceManager ! RequestTrackDevice("group:0", "device:1", deviceProbe.ref)
      val toShutdown = deviceProbe.receiveMessage().device

      deviceManager ! RequestTrackDevice("group:1", "device:2", deviceProbe.ref)
      deviceProbe.receiveMessage()

      toShutdown ! Passivate
      deviceProbe.expectTerminated(toShutdown.ref)

      deviceProbe.awaitAssert {
        deviceManager ! RequestDeviceList(0, "group:0", deviceListProbe.ref)
        deviceListProbe.expectMessage(ReplyDeviceList(0, Set("device:0")))

        deviceManager ! RequestDeviceList(1, "group:1", deviceListProbe.ref)
        deviceListProbe.expectMessage(ReplyDeviceList(1, Set("device:2")))
      }
    }
  }

  "DeviceGroupQuery" must {
    "return temperature value from working devices" in {
      val probeAllTemps = createTestProbe[ReplyAllTemps]()

      val device0 = createTestProbe[Device.Command]()
      val device1 = createTestProbe[Device.Command]()
      val devices = Map("0" -> device0.ref, "1" -> device1.ref)

      val queryActor =
        spawn(DeviceGroupQuery(devices, 0, probeAllTemps.ref, 3.seconds))

      device0.expectMessageType[ReadTemp]
      device1.expectMessageType[ReadTemp]

      queryActor ! WrappedRespondTemp(Device.RespondTemp(0, "0", Some(3.0)))
      queryActor ! WrappedRespondTemp(Device.RespondTemp(0, "1", Some(6.0)))

      probeAllTemps.expectMessage(
        ReplyAllTemps(
          requestId = 0,
          temps = Map("0" -> Temp(3.0), "1" -> Temp(6.0)))
      )
    }

    "return temperature not available for devices that have no reading" in {
      val probeAllTemps = createTestProbe[ReplyAllTemps]()

      val device0 = createTestProbe[Device.Command]()
      val device1 = createTestProbe[Device.Command]()
      val devices = Map("0" -> device0.ref, "1" -> device1.ref)

      val queryActor =
        spawn(DeviceGroupQuery(devices, 0, probeAllTemps.ref, 3.seconds))

      device0.expectMessageType[ReadTemp]
      device1.expectMessageType[ReadTemp]

      queryActor ! WrappedRespondTemp(Device.RespondTemp(0, "0", None))
      queryActor ! WrappedRespondTemp(Device.RespondTemp(0, "1", Some(6.0)))

      probeAllTemps.expectMessage(
        ReplyAllTemps(
          requestId = 0,
          temps = Map("0" -> TempNotAvailable, "1" -> Temp(6.0)))
      )
    }

    "return DeviceNotAvailable when the actor has stopped before responding" in {
      val probeAllTemps = createTestProbe[ReplyAllTemps]()

      val device0 = createTestProbe[Device.Command]()
      val device1 = createTestProbe[Device.Command]()
      val devices = Map("0" -> device0.ref, "1" -> device1.ref)

      val queryActor =
        spawn(DeviceGroupQuery(devices, 0, probeAllTemps.ref, 3.seconds))

      device0.expectMessageType[ReadTemp]
      device1.expectMessageType[ReadTemp]

      queryActor ! WrappedRespondTemp(Device.RespondTemp(0, "0", Some(3.0)))
      device1.stop()

      probeAllTemps.expectMessage(
        ReplyAllTemps(
          requestId = 0,
          temps = Map("0" -> Temp(3.0), "1" -> DeviceNotAvailable))
      )
    }

    "return the correct temp value if a device stops after responding" in {
      val probeAllTemps = createTestProbe[ReplyAllTemps]()

      val device0 = createTestProbe[Device.Command]()
      val device1 = createTestProbe[Device.Command]()
      val devices = Map("0" -> device0.ref, "1" -> device1.ref)

      val queryActor =
        spawn(DeviceGroupQuery(devices, 0, probeAllTemps.ref, 3.seconds))

      device0.expectMessageType[ReadTemp]
      device1.expectMessageType[ReadTemp]

      queryActor ! WrappedRespondTemp(Device.RespondTemp(0, "0", Some(3.0)))
      queryActor ! WrappedRespondTemp(Device.RespondTemp(0, "1", Some(6.0)))
      device1.stop()

      probeAllTemps.expectMessage(
        ReplyAllTemps(
          requestId = 0,
          temps = Map("0" -> Temp(3.0), "1" -> Temp(6.0)))
      )
    }

    "return DeviceTimeout if the device does not respond in time" in {
      val probeAllTemps = createTestProbe[ReplyAllTemps]()

      val device0 = createTestProbe[Device.Command]()
      val device1 = createTestProbe[Device.Command]()
      val devices = Map("0" -> device0.ref, "1" -> device1.ref)

      val queryActor =
        spawn(DeviceGroupQuery(devices, 0, probeAllTemps.ref, 2.seconds))

      device0.expectMessageType[ReadTemp]
      device1.expectMessageType[ReadTemp]

      queryActor ! WrappedRespondTemp(Device.RespondTemp(0, "0", Some(3.0)))
      //no response from device1

      probeAllTemps.expectMessage(
        ReplyAllTemps(
          requestId = 0,
          temps = Map("0" -> Temp(3.0), "1" -> DeviceTimeOut))
      )
    }
  }
}
