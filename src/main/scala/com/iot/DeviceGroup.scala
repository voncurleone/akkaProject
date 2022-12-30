package com.iot

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, LoggerOps}

object DeviceGroup {
  trait Command

  def apply(groupId: String): Behavior[Command] =
    Behaviors.setup(new DeviceGroup(_, groupId))
}

class DeviceGroup(context: ActorContext[DeviceGroup.Command], groupId: String)
  extends AbstractBehavior[DeviceGroup.Command](context) {
  import DeviceGroup._
  import DeviceManager.{RequestTrackDevice, DeviceRegistered, RequestDeviceList, ReplyDeviceList}

  private var devices: Map[String, ActorRef[Device.Command]] = Map()
  context.log.info("DeviceGroup {} started", groupId)

  override def onMessage(msg: DeviceGroup.Command): Behavior[DeviceGroup.Command] = {
    msg match {
      case RequestTrackDevice(`groupId`, deviceId, replyTo) =>
        devices.get(deviceId) match {
          case Some(deviceActor) =>
            replyTo ! DeviceRegistered(deviceActor)
          case None =>
            //create new device
            context.log.info("Creating device actor for {}", deviceId)
            val deviceActor = context.spawn(Device(groupId, deviceId), s"Device: $deviceId")
            //add new devices to the device map
            devices += deviceId -> deviceActor
            replyTo ! DeviceRegistered(deviceActor)
        }
        this

      case RequestTrackDevice(gId, _, _) =>
        context.log.warn2("Ignoring request for {}. This actor is responsible for device group: {}", gId, groupId)
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("DeviceGroup {} stopped", groupId)
      this
  }
}
