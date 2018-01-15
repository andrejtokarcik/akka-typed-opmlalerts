name := "akka-opml-alerts"

scalaVersion := "2.12.4"

lazy val akkaVersion = "2.5.9"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.rometools" % "rome" % "1.9.0"
)

// test-related dependencies
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.0.4" % Test
)
