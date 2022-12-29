package com.iot



import akka.actor.typed.{ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import java.lang.Thread.sleep

object IotSupervisor {
  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing](new IotSupervisor(_))
}

class IotSupervisor(context: ActorContext[Nothing]) extends AbstractBehavior[Nothing](context) {
  private val name = context.self.path
  context.log.info(s"[$name] Iot application started")

  override def onMessage(msg: Nothing): Behavior[Nothing] = {
    //todo: handle messages
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[Nothing]] = {
    case PostStop =>
      context.log.info(s"[$name] Iot application stopped")
      this
  }
}

object main extends App {
  val system = ActorSystem[Nothing](IotSupervisor(), "IotSupervisor")
  sleep(1000)
  system.terminate()
}
