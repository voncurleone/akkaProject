package com.iot

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}


object Device {
  sealed trait Command
  final case class ReadTemp(requestId: Long, replyTo: ActorRef[RespondTemp]) extends Command
  final case class RespondTemp(requestId: Long, value: Option[Double])
  final case class RecordTemp(requestId: Long, value: Double, replyTo: ActorRef[TempRecorded]) extends Command
  final case class TempRecorded(requestId: Long)

  def apply(groupId: String, deviceId: String): Behavior[Command] =
    Behaviors.setup(new Device(_, groupId, deviceId))
}

class Device(context: ActorContext[Device.Command], groupId: String, deviceId: String)
  extends AbstractBehavior[Device.Command](context) {
  import Device._

  private var lastTemp: Option[Double] = None
  context.log.info2("Device actor started {}-{}", groupId, deviceId)

  override def onMessage(msg: Device.Command): Behavior[Device.Command] = {
    msg match {
      case ReadTemp(requestId, replyTo) =>
        replyTo ! RespondTemp(requestId, lastTemp)
        this

      case RecordTemp(requestId, value, replyTo) =>
        context.log.info2("Recorded temp {} with {}", value, requestId)
        lastTemp = Some(value)
        replyTo ! TempRecorded(requestId)
        this
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info2("Device actor stopped {}-{}", groupId, deviceId)
      this
  }
}
