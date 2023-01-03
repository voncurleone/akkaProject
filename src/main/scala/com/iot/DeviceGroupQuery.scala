package com.iot


import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {
  trait Command

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


  override def onMessage(msg: DeviceGroupQuery.Command): Behavior[DeviceGroupQuery.Command] = ???
}
