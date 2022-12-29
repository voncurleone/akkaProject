ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "akkaProject"
  )

val AkkaVersion = "2.7.0"
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion