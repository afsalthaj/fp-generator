name := "fp-generator"

version := "5.0.0"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.2.7",
  "co.fs2" %% "fs2-core" % "0.10.4",
  "co.fs2" %% "fs2-io" % "0.10.4"
)

fork := true