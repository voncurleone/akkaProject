package com.iot


import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {
  trait Command
  final case class WrappedRespondTemp(reply: Device.RespondTemp) extends Command
  private case object ConnectionTimeout extends Command
  private final case class DeviceTerminated(deviceId: String) extends Command

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

  timers.startSingleTimer(ConnectionTimeout, ConnectionTimeout, timeout)
  private val responseAdapter = context.messageAdapter(WrappedRespondTemp.apply)
  private var responses = Map[String, TempReading]()
  private var awaitingResponse = devices.keySet

  devices.foreach {
    case (deviceId, device) =>
      context.watchWith(device, DeviceTerminated(deviceId))
      device ! Device.ReadTemp(0, responseAdapter)
  }

  override def onMessage(msg: DeviceGroupQuery.Command): Behavior[DeviceGroupQuery.Command] = msg match {
    case WrappedRespondTemp(reply) => onRespondTemp(reply)
    case ConnectionTimeout => onConnectionTimeout()
    case DeviceTerminated(deviceId) => onDeviceTerminated(deviceId)
  }

  private def onRespondTemp(response: Device.RespondTemp): Behavior[Command] = {
    val reading = response.value match {
      case None => TempNotAvailable
      case Some(value) => Temp(value)
    }

    val deviceId = response.deviceId
    responses += deviceId -> reading
    awaitingResponse -= deviceId

    respondWhenAllCollected()
  }
  private def onConnectionTimeout(): Behavior[Command] = {
    responses ++= awaitingResponse.map(deviceId => deviceId -> DeviceTimeOut)
    awaitingResponse = Set.empty

    respondWhenAllCollected()
  }
  private def onDeviceTerminated(deviceId: String): Behavior[Command] = {
    if(awaitingResponse.contains(deviceId)) {
      responses += deviceId -> DeviceNotAvailable
      awaitingResponse -= deviceId
    }

    respondWhenAllCollected()
  }

  private def respondWhenAllCollected(): Behavior[Command] = {
    if( awaitingResponse.isEmpty) {
      requester ! ReplyAllTemps(requestId, responses)
      Behaviors.stopped
    } else {
      this
    }
  }
}
