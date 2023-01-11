package com.iot

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.FiniteDuration

/**
 * Factory object for [[DeviceGroupQuery]]
 *
 * Contains [[com.iot.DeviceGroupQuery.Command message protocol]] for DeviceGroupQuery
 */
object DeviceGroupQuery {

  /**
   * Root type for [[DeviceGroupQuery]] message protocol
   *
   * Children:
   *  - [[WrappedRespondTemp]]
   *  - [[ConnectionTimeout]]
   *  - [[DeviceTerminated]]
   */
  trait Command

  /**
   * Wrapper for a [[Device.RespondTemp RespondTemp]] message.
   *
   * @param reply A [[Device.RespondTemp]] Message
   */
  final case class WrappedRespondTemp(reply: Device.RespondTemp) extends Command

  /**
   * Message that signals that the query has timed out
   */
  private case object ConnectionTimeout extends Command

  /**
   * Message that signals that a tracked [[Device]] has been terminated
   *
   * @param deviceId Unique Id for identifying the [[Device]] that has been terminated
   */
  private final case class DeviceTerminated(deviceId: String) extends Command

  /**
   * Creates a DeviceGroupQuery actor
   *
   * @param devices a mapping of deviceIds to [[Device Devices]] that are being queried
   * @param requestId Unique Id for identifying the request
   * @param requester The [[DeviceManager actor]] that initiated the request. Also the actor to reply to
   * @param timeout The [[FiniteDuration duration]] before the query times out
   * @return A [[DeviceGroupQuery]] actor
   */
  def apply(
           devices: Map[String, ActorRef[Device.Command]],
           requestId: Long,
           requester: ActorRef[DeviceManager.ReplyAllTemps],
           timeout: FiniteDuration ): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new DeviceGroupQuery(devices, requestId, requester, timeout, context, timers)
      }
    }
  }
}

/**
 * DeviceGroupQuery actor. This actor represents an active query and stops after the completion of the query
 *
 * @param devices A mapping of deviceIds to [[Device Devices]] that are being queried
 * @param requestId Unique Id for identifying the request
 * @param requester The [[DeviceManager actor]] that initiated the request. Also the actor to reply to
 * @param timeout The [[FiniteDuration duration]] before the query times out
 * @param context DeviceGroupQuerys [[https://doc.akka.io/api/akka/current/akka/actor/ActorContext.html ActorContext]]
 * @param timers [[TimerScheduler]] of type [[com.iot.DeviceGroupQuery.Command]]. Used to time out query after a [[FiniteDuration]]
 */
class DeviceGroupQuery(
                      devices: Map[String, ActorRef[Device.Command]],
                      requestId: Long,
                      requester: ActorRef[DeviceManager.ReplyAllTemps],
                      timeout: FiniteDuration,
                      context: ActorContext[DeviceGroupQuery.Command],
                      timers: TimerScheduler[DeviceGroupQuery.Command]
                      ) extends AbstractBehavior[DeviceGroupQuery.Command](context) {
  import DeviceGroupQuery._
  import DeviceManager.{TempReading, Temp, TempNotAvailable, DeviceNotAvailable, DeviceTimeOut, ReplyAllTemps}

  context.log.info(s"Starting query id: $requestId for actor: $requester")

  timers.startSingleTimer(ConnectionTimeout, ConnectionTimeout, timeout)
  private val responseAdapter = context.messageAdapter(WrappedRespondTemp.apply)
  private var responses = Map[String, TempReading]()
  private var awaitingResponse = devices.keySet

  devices.foreach {
    case (deviceId, device) =>
      context.watchWith(device, DeviceTerminated(deviceId))
      device ! Device.ReadTemp(0, responseAdapter)
  }

  /**
   * handles [[Command messages]] sent to the DeviceGroupQuery actor
   *
   * [[Command Messages]] supported by DeviceGroupQuery:
   *  - [[WrappedRespondTemp]]
   *  - [[ConnectionTimeout]]
   *  - [[DeviceTerminated]]
   *
   * @param msg The [[Command message]]
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  override def onMessage(msg: DeviceGroupQuery.Command): Behavior[DeviceGroupQuery.Command] = msg match {
    case WrappedRespondTemp(reply) => onRespondTemp(reply)
    case ConnectionTimeout => onConnectionTimeout()
    case DeviceTerminated(deviceId) => onDeviceTerminated(deviceId)
  }

  /**
   * Function for handling a [[WrappedRespondTemp]] message
   *
   * @param response A [[Device.RespondTemp]] message from a [[Device]] in the [[DeviceGroup]]
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  private def onRespondTemp(response: Device.RespondTemp): Behavior[Command] = {

    val reading = response.value match {
      case None => TempNotAvailable
      case Some(value) => Temp(value)
    }

    val deviceId = response.deviceId
    context.log.info(s"Received response from deviceId: $deviceId of: ${response.value}")

    responses += deviceId -> reading
    awaitingResponse -= deviceId

    respondWhenAllCollected()
  }

  /**
   * Function for handling a [[ConnectionTimeout]] message
   *
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  private def onConnectionTimeout(): Behavior[Command] = {
    responses ++= awaitingResponse.map(deviceId => deviceId -> DeviceTimeOut)
    awaitingResponse = Set.empty
    context.log.info("Connection timout Message received")

    respondWhenAllCollected()
  }

  /**
   * Function for handling a [[DeviceTerminated]] message
   *
   * @param deviceId A unique Id for identifying a [[Device]]
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  private def onDeviceTerminated(deviceId: String): Behavior[Command] = {
    if(awaitingResponse.contains(deviceId)) {
      context.log.info(s"device: $deviceId has terminated before responding")
      responses += deviceId -> DeviceNotAvailable
      awaitingResponse -= deviceId
    }

    respondWhenAllCollected()
  }

  /**
   * Function that checks if the query is complete. Responds with the result if it is complete otherwise it waits for the rest of the [[Device devices]]
   *
   * @return A [[https://doc.akka.io/api/akka/current/akka/actor/typed/Behavior.html Behavior]] of type [[Command]]
   */
  private def respondWhenAllCollected(): Behavior[Command] = {
    if( awaitingResponse.isEmpty) {
      context.log.info("Sending response and stopping")
      requester ! ReplyAllTemps(requestId, responses)
      Behaviors.stopped
    } else {
      this
    }
  }
}
