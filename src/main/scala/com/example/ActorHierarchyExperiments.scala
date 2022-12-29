package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import java.lang.Thread.sleep

object ActorHierarchyExperiments extends App {
  val system = ActorSystem(UserGaurdian(), "UserGaurdian")
  system ! "start"
  sleep(1000)
  system.terminate()
}

object PrintMyActor {
  def apply(): Behavior[String] =
    Behaviors.setup(context => new PrintMyActor(context))
}

class PrintMyActor(context: ActorContext[String]) extends AbstractBehavior[String](context) {
  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "print" =>
        val child = context.spawn(Behaviors.empty[String], "Child")
        println(s"Child: $child")
        this
    }
  }
}

object UserGaurdian {
  def apply(): Behavior[String] =
    Behaviors.setup( context => new UserGaurdian(context) )
}

class UserGaurdian(context: ActorContext[String]) extends AbstractBehavior[String](context) {
  override def onMessage(msg: String): Behavior[String] = {
    msg match {
      case "start" =>
        val parent = context.spawn(PrintMyActor(), "Parent")
        println(s"Parent: $parent")
        parent ! "print"
        this
    }
  }
}
