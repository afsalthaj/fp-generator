import sbt.CrossVersion

name := "fp-generator"

version := "5.1.8"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.2.7",
  "co.fs2" %% "fs2-core" % "0.10.4",
  "co.fs2" %% "fs2-io" % "0.10.4",
  "org.specs2" %% "specs2-core" % "4.0.3",
  "org.scalacheck"  %% "scalacheck" % "1.13.5",
  "org.specs2" %% "specs2-scalacheck" % "4.0.3",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.2.7",
  "com.chuusai"     %% "shapeless" % "2.3.3"
)

fork := true

addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.6" cross CrossVersion.binary)
