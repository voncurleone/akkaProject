package com.iot

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}

/**
 * Factory object for [[com.iot.Device]].
 *
 * Contains message protocol for [[com.iot.Device]].
 *
 */
object Device {
  /**
   * Parent type of all messages used by [[com.iot.Device]].
   *
   * Children:
   *  - [[ReadTemp]]
   *  - [[RespondTemp]]
   *  - [[RecordTemp]]
   *  - [[TempRecorded]]
   *  - [[Passivate]]
   */
  sealed trait Command

  /**
   * Message used to read the temp from a device.
   *
   * @param requestId Uniquely identifies the request
   * @param replyTo Actor that the device will send the temp to in a [[RespondTemp]] message
   */
  final case class ReadTemp(requestId: Long, replyTo: ActorRef[RespondTemp]) extends Command
  /**
   * Message used to send the temp to the replyTo actor of the [[ReadTemp]] message
   *
   * @param requestId Uniquely identifies the request
   * @param deviceId String that uniquely identifies the device responding
   * @param value A [[https://www.scala-lang.org/api/2.13.3/scala/Option.html Option]] that contains a temp if one is available
   */
  final case class RespondTemp(requestId: Long, deviceId: String, value: Option[Double])

  /**
   * Message used to record a temp to the device actor.
   *
   * @param requestId Uniquely identifies the request
   * @param value Temp being recorded
   * @param replyTo Actor that will be notified when the temp is recorded with [[TempRecorded]] message
   */
  final case class RecordTemp(requestId: Long, value: Double, replyTo: ActorRef[TempRecorded]) extends Command

  /**
   * Message used to indicate that a temp has been recorded.
   *
   * @param requestId Uniquely identifies the request
   */
  final case class TempRecorded(requestId: Long)

  /**
   * Message used to stop a device actor.
   */
  case object Passivate extends Command

  /**
   * Creates a device actor.
   *
   * @param groupId Group the device belongs to
   * @param deviceId uniquely identify the device
   * @return A device actor [[Device]]
   */
  def apply(groupId: String, deviceId: String): Behavior[Command] =
    Behaviors.setup(new Device(_, groupId, deviceId))
}

/**
 * Device actor.
 *
 *  1. Waits for a request for the current temp
 *  1. Responds to the request with either:
 *    i. The current temp wrapped in an [[https://www.scala-lang.org/api/2.13.3/scala/Option.html Option]]
 *    i. An [[https://www.scala-lang.org/api/2.13.3/scala/Option.html Option]] containing None
 *
 * @param context Devices [[https://doc.akka.io/api/akka/current/akka/actor/ActorContext.html ActorContext]]
 * @param groupId Identifies the devices [[DeviceGroup device group]]
 * @param deviceId Identifies the device within the [[DeviceGroup device group]]
 */
class Device(context: ActorContext[Device.Command], groupId: String, deviceId: String)
  extends AbstractBehavior[Device.Command](context) {
  import Device._

  private var lastTemp: Option[Double] = None
  context.log.info2("Device actor started {}-{}", groupId, deviceId)

  /**
   * Handles [[Command messages]] send to the [[Device device]]
   *
   * [[Command Messages]] that are supported by Device:
   *  - [[ReadTemp]]
   *  - [[RecordTemp]]
   *  - [[Passivate]]
   *
   * @param msg A [[Command message]] to be handled
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  override def onMessage(msg: Device.Command): Behavior[Device.Command] = {
    msg match {
      case ReadTemp(requestId, replyTo) =>
        replyTo ! RespondTemp(requestId, deviceId, lastTemp)
        this

      case RecordTemp(requestId, value, replyTo) =>
        context.log.info2("Recorded temp {} with {}", value, requestId)
        lastTemp = Some(value)
        replyTo ! TempRecorded(requestId)
        this

      case Passivate =>
        Behaviors.stopped
    }
  }

  /**
   * Handles [[https://doc.akka.io/api/akka/current/akka/actor/typed/Signal.html signals]] received by Device
   *
   * [[https://doc.akka.io/api/akka/current/akka/actor/typed/Signal.html Signals]] handled:
   *  - [[https://doc.akka.io/api/akka/current/akka/actor/typed/PostStop.html PostStop]]
   *
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info2("Device actor stopped {}-{}", groupId, deviceId)
      this
  }
}
