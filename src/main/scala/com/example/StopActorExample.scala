package com.example

import akka.actor.typed.{ActorSystem, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import java.lang.Thread.sleep

object StopActorOne {
  def apply(): Behavior[String] =
    Behaviors.setup( context => new StopActorOne(context) )
}

class StopActorOne(context: ActorContext[String]) extends AbstractBehavior[String](context) {
  println("One Started")
  context.spawn(StopActorTwo(), "Two")

  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "stop" => Behaviors.stopped
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[String]] = {
    case PostStop =>
      println("One stopped")
      this
  }
}

object StopActorTwo {
  def apply(): Behavior[String] =
    Behaviors.setup( new StopActorTwo(_) )
}

class StopActorTwo(context: ActorContext[String]) extends AbstractBehavior[String](context) {
  println("Two started")
  override def onMessage(msg: String): Behavior[String] = {
    //no messages handled by this actor
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[String]] = {
    case PostStop =>
      println("Two Stopped")
      this
  }
}
object StopActorExample extends App {
  val system = ActorSystem(StopActorOne(), "One")
  system ! "stop"
  sleep(1000)
  system.terminate()
}
