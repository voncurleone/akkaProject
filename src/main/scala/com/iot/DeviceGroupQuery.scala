package com.iot


import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {
  trait Command
  final case class WrappedRespondTemp(reply: Device.RespondTemp) extends Command
  private case object ConnectionTimout extends Command
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

  timers.startSingleTimer(ConnectionTimout, ConnectionTimout, timeout)
  private val responseAdapter = context.messageAdapter(WrappedRespondTemp.apply)

  devices.foreach {
    case (deviceId, device) =>
      context.watchWith(device, DeviceTerminated(deviceId))
      device ! Device.ReadTemp(0, responseAdapter)
  }

  override def onMessage(msg: DeviceGroupQuery.Command): Behavior[DeviceGroupQuery.Command] = ???
}
