ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "akkaProject"
  )

val AkkaVersion = "2.7.0"
libraryDependencies ++= Seq (
  //akka
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,

  //akka test kit
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion,

  //logging
  "ch.qos.logback" % "logback-classic" % "1.4.5",

  //scala test
  "org.scalatest" %% "scalatest" % "3.2.14" % Test
)