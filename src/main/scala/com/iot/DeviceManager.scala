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

  //command for a terminated DeviceGroup
  final case class DeviceGroupTerminated(groupId: String) extends DeviceManager.Command

  protected final case class RequestAllTemps(requestId: Long, groupId: String, replyTo: ActorRef[ReplyAllTemps])
    extends DeviceGroupQuery.Command
      with DeviceManager.Command
      with DeviceGroup.Command

  final case class ReplyAllTemps(requestId: Long, temps: Map[String, TempReading])

  sealed trait TempReading
  final case class Temp(value: Double) extends TempReading
  case object TempNotAvailable extends TempReading
  case object DeviceNotAvailable extends TempReading
  case object DeviceTimeOut extends TempReading

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
      case request @ RequestTrackDevice(groupId, _, _) =>
        groups.get(groupId) match {
          case Some(ref) =>
            ref ! request
          case None =>
            context.log.info("Creating DeviceGroup actor {}", groupId)
            val deviceGroup = context.spawn(DeviceGroup(groupId), s"$groupId")
            context.watchWith(deviceGroup, DeviceGroupTerminated(groupId))
            deviceGroup ! request
            groups += groupId -> deviceGroup
        }
        this

      case request @ RequestDeviceList(requestId, groupId, replyTo) =>
        groups.get(groupId) match {
          case Some(ref) =>
            ref ! request

          case None =>
            replyTo ! ReplyDeviceList(requestId, Set.empty)
        }
        this

      case DeviceGroupTerminated(groupId) =>
        context.log.info("Device group stopped {}", groupId)
        groups -= groupId
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Device manager has been stopped")
      this
  }
}
