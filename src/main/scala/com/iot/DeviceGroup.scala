package com.iot

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, LoggerOps}

import scala.concurrent.duration.DurationInt

/**
 * Factory object for [[DeviceGroup]]
 *
 * Contains [[com.iot.DeviceGroup.Command message protocol]] for [[DeviceGroup]]
 */
object DeviceGroup {
  /**
   * Root type for [[DeviceGroup]] message protocol
   *
   * child: [[DeviceTerminated]]
   */
  trait Command

  /**
   * Message used to signal that a device actor has stopped.
   *
   * @param deviceActor The [[Device device actor]] that stopped
   * @param groupId unique group id
   * @param deviceId unique device id
   */
  final case class DeviceTerminated(deviceActor: ActorRef[Device.Command], groupId: String, deviceId: String) extends Command

  /**
   * Creates a DeviceGroup actor.
   *
   * @param groupId unique group id
   * @return A DeviceGroup actor
   */
  def apply(groupId: String): Behavior[Command] =
    Behaviors.setup(new DeviceGroup(_, groupId))
}

/**
 * DeviceGroup actor. This actor manages a group of [[Device]] actors.
 *
 * @param context DeviceGroup's [[https://doc.akka.io/api/akka/current/akka/actor/ActorContext.html ActorContext]]
 * @param groupId unique group identifier
 */
class DeviceGroup(context: ActorContext[DeviceGroup.Command], groupId: String)
  extends AbstractBehavior[DeviceGroup.Command](context) {
  import DeviceGroup._
  import DeviceManager.{
    RequestTrackDevice,
    DeviceRegistered,
    RequestDeviceList,
    ReplyDeviceList,
    RequestAllTemps
  }

  private var devices: Map[String, ActorRef[Device.Command]] = Map()
  context.log.info("DeviceGroup {} started", groupId)

  /**
   * Handles [[Command messages]] sent to the DeviceGroup actor.
   *
   * [[Command Messages]] supported by DeviceGroup:
   *  - [[RequestTrackDevice]]
   *  - [[RequestDeviceList]]
   *  - [[DeviceTerminated]]
   *  - [[RequestAllTemps]]
   *
   * @param msg A [[Command message]] received by the DeviceGroup actor.
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  override def onMessage(msg: DeviceGroup.Command): Behavior[DeviceGroup.Command] = {
    msg match {
      case RequestTrackDevice(`groupId`, deviceId, replyTo) =>
        devices.get(deviceId) match {
          case Some(deviceActor) =>
            replyTo ! DeviceRegistered(deviceActor)
          case None =>
            //create new device
            context.log.info("Creating device actor for {}", deviceId)
            val deviceActor = context.spawn(Device(groupId, deviceId), s"Device:$deviceId.")
            //watch the new device
            context.watchWith(deviceActor, DeviceTerminated(deviceActor, groupId, deviceId))
            //add new devices to the device map
            devices += deviceId -> deviceActor
            replyTo ! DeviceRegistered(deviceActor)
        }
        this

      case RequestTrackDevice(gId, _, _) =>
        context.log.warn2("Ignoring request for {}. This actor is responsible for device group: {}", gId, groupId)
        this

      case RequestDeviceList(requestId, gId, replyTo) =>
        if( gId == groupId ) {
          replyTo ! ReplyDeviceList(requestId, devices.keySet)
          this
        }
        else Behaviors.unhandled

      case DeviceTerminated(_, _, deviceId) =>
        context.log.info("Device actor {} has been terminated.", deviceId)
        devices -= deviceId
        this

      case RequestAllTemps(requestId, gId, replyTo) =>
        if(gId == groupId) {
          context.spawnAnonymous(DeviceGroupQuery(devices, requestId, replyTo, 3.seconds))
          this
        } else Behaviors.unhandled
    }
  }

  /**
   * Handles [[https://doc.akka.io/api/akka/current/akka/actor/typed/Signal.html signals]] received by DeviceGroup
   *
   * [[https://doc.akka.io/api/akka/current/akka/actor/typed/Signal.html Signals]] handled:
   *  - [[https://doc.akka.io/api/akka/current/akka/actor/typed/PostStop.html PostStop]]
   *
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("DeviceGroup {} stopped", groupId)
      this
  }
}
