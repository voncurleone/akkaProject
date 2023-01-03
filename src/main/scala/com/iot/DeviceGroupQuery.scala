package com.iot

import akka.actor.typed.ActorRef

object DeviceGroupQuery {
  trait Command
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
}
