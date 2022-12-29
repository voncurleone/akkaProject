package com.example

import akka.actor.typed.{ActorSystem, Behavior, PostStop, PreRestart, Signal, SupervisorStrategy}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import java.lang.Thread.sleep


object Supervisor {
  def apply(): Behavior[String] =
    Behaviors.setup(new Supervisor(_))
}

class Supervisor(context: ActorContext[String]) extends AbstractBehavior[String](context) {
  private val child = context.spawn(
    Behaviors.supervise(Supervised()).onFailure(SupervisorStrategy.restart),
    "Supervisor"
  )

  override def onMessage(msg: String): Behavior[String] =
    msg match {
      case "failChild" =>
        child ! "fail"
        this
    }
}

object Supervised {
  def apply(): Behavior[String] =
    Behaviors.setup(new Supervised(_))
}

class Supervised(context: ActorContext[String]) extends AbstractBehavior[String](context) {
  println("Supervised Actor Started")

  override def onMessage(msg: String): Behavior[String] =
    msg match {
      case "fail" =>
        println("Supervised Actor Failing")
        throw new Exception("I failed")
    }

  override def onSignal: PartialFunction[Signal, Behavior[String]] = {
    case PreRestart =>
      println("Supervised Actor Restarting")
      this

    case PostStop =>
      println("Supervised Actor Stopped")
      this
  }
}
object SupervisionExample extends App {
  val supervisor = ActorSystem(Supervisor(), "SuperVisor")
  supervisor ! "failChild"
  sleep(1000)
  println("Terminating ActorSystem")
  supervisor.terminate()
}
