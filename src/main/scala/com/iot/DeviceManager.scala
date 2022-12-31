package com.iot

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}

object DeviceManager {
  trait Command
  final case class RequestTrackDevice(groupId: String, deviceId: String, replyTo: ActorRef[DeviceRegistered])
    extends DeviceManager.Command
    with DeviceGroup.Command
  final case class DeviceRegistered(device: ActorRef[Device.Command])

  //commands for querying and testing
  final case class RequestDeviceList(requestId: Long, groupId: String, replyTo: ActorRef[ReplyDeviceList])
    extends DeviceManager.Command
    with DeviceGroup.Command

  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  def apply(): Behavior[Command] =
    Behaviors.setup( new DeviceManager(_) )
}

class DeviceManager(context: ActorContext[DeviceManager.Command])
  extends AbstractBehavior[DeviceManager.Command](context) {
  import DeviceManager._

  var groups: Map[String, ActorRef[DeviceGroup.Command]] = Map()
  context.log.info("Device manager started")

  override def onMessage(msg: DeviceManager.Command): Behavior[DeviceManager.Command] = {
    msg match {
      case RequestTrackDevice(groupId, deviceId, replyTo) =>
        ???
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Device manager has been stopped")
      this
  }
}
