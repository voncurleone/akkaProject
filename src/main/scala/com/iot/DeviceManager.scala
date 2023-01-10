package com.iot

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}

/**
 * Factory object for [[DeviceManager]]
 *
 * Contains [[com.iot.DeviceGroup.Command message protocol]] for [[DeviceManager]]
 */
object DeviceManager {

  /**
   * Root type for [[DeviceManager]] message protocol
   *
   * Children:
   *  - [[RequestTrackDevice]]
   *  - [[RequestDeviceList]]
   */
  trait Command

  /**
   * Message used to request a [[DeviceGroup]] to track a [[Device]]
   *
   * @param groupId unique Id for the device group that will track the [[Device]]
   * @param deviceId unique Id to identify the [[Device]]
   * @param replyTo Actor that will be notified when the [[Device]] is registered in the
   *                [[DeviceGroup]] with a [[DeviceRegistered message]]
   */
  final case class RequestTrackDevice(groupId: String, deviceId: String, replyTo: ActorRef[DeviceRegistered])
    extends DeviceManager.Command
    with DeviceGroup.Command

  /**
   * Message sent when a [[Device]] is registered to a [[DeviceGroup]]
   *
   * @param device A [[Device]] actor
   */
  final case class DeviceRegistered(device: ActorRef[Device.Command])

  /**
   * Message used to obtain a list of the [[Device devices]] tracked by the [[DeviceGroup]]
   *
   * @param requestId Unique Id for identifying the request
   * @param groupId Unique Id for identifying the group
   * @param replyTo Actor to send [[RequestDeviceList response]] to
   */
  final case class RequestDeviceList(requestId: Long, groupId: String, replyTo: ActorRef[ReplyDeviceList])
    extends DeviceManager.Command
    with DeviceGroup.Command

  /**
   * Message sent as a reply to a [[RequestDeviceList]] message
   *
   * @param requestId Unique Id for identifying the request
   * @param ids Set of [[Device]] Ids that the [[DeviceGroup]] tracks
   */
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  /**
   * Message sent to the [[DeviceManager]] actor when one of the [[DeviceGroup DeviceGroups]] it manages is stopped
   *
   * @param groupId Unique Id that Identifies the [[DeviceGroup]] that has stopped
   */
  final case class DeviceGroupTerminated(groupId: String) extends DeviceManager.Command

  /**
   * Message used to obtain temps from all the [[Device Devices]] tracked by the [[DeviceGroup]]
   *
   * @param requestId Unique Id to identify the request
   * @param groupId Unique Id to identify the [[DeviceGroup]]
   * @param replyTo Actor to send [[ReplyAllTemps response]] to
   */
  final case class RequestAllTemps(requestId: Long, groupId: String, replyTo: ActorRef[ReplyAllTemps])
    extends DeviceGroupQuery.Command
      with DeviceManager.Command
      with DeviceGroup.Command

  /**
   * Message sent as a reply to a [[RequestAllTemps]] message
   *
   * @param requestId Unique Id to identify the request
   * @param temps A map from DeviceId to [[TempReading]]
   */
  final case class ReplyAllTemps(requestId: Long, temps: Map[String, TempReading])

  /**
   * Type representing a Temp reading
   *
   * It covers the cases:
   *  - The [[Device]] has a temp available: [[Temp]]
   *  - The [[Device]] has no reading available: [[TempNotAvailable]]
   *  - The [[Device]] is not available to provide a reading: [[DeviceNotAvailable]]
   *  - The [[Device]] has timed out: [[DeviceTimeOut]]
   */
  sealed trait TempReading

  /**
   * The [[Device]] has a temp available
   *
   * @param value The temp reading
   */
  final case class Temp(value: Double) extends TempReading

  /**
   * The [[Device]] has no reading available
   */
  case object TempNotAvailable extends TempReading

  /**
   * The [[Device]] is not available to provide a reading
   */
  case object DeviceNotAvailable extends TempReading

  /**
   * The [[Device]] has timed out
   */
  case object DeviceTimeOut extends TempReading

  /**
   * Creates a DeviceManager actor
   *
   * @return A [[DeviceManager]] actor
   */
  def apply(): Behavior[Command] =
    Behaviors.setup( new DeviceManager(_) )
}

/**
 * DeviceManager actor. This actor manages a group of [[DeviceGroup DeviceGroups]]
 *
 * @param context DeviceManagers [[https://doc.akka.io/api/akka/current/akka/actor/ActorContext.html ActorContext]]
 */
class DeviceManager(context: ActorContext[DeviceManager.Command])
  extends AbstractBehavior[DeviceManager.Command](context) {
  import DeviceManager._

  var groups: Map[String, ActorRef[DeviceGroup.Command]] = Map()
  context.log.info("Device manager started")

  /**
   * Handles [[Command messages]] sent to DeviceManager actor
   *
   * [[Command Messages]] supported by DeviceManager:
   *  - [[RequestTrackDevice]]
   *  - [[RequestDeviceList]]
   *  - [[DeviceGroupTerminated]]
   *
   * @param msg The [[Command message]]
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
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

  /**
   * Handles [[https://doc.akka.io/api/akka/current/akka/actor/typed/Signal.html signals]] received by [[DeviceManager]]
   *
   * [[https://doc.akka.io/api/akka/current/akka/actor/typed/Signal.html Signals]] handled:
   *  - [[https://doc.akka.io/api/akka/current/akka/actor/typed/PostStop.html PostStop]]
   *
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Device manager has been stopped")
      this
  }
}
