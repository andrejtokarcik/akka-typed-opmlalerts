name := "akka-opml-alerts"

scalaVersion := "2.12.4"

lazy val akkaVersion = "2.5.9"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.rometools" % "rome" % "1.9.0",
  "com.rometools" % "rome-opml" % "1.9.0",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "org.scalaz" %% "scalaz-core" % "7.3.0-M19"
)

// test-related dependencies
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.0.4" % Test
)

scalacOptions in (Compile, compile) ++= Seq(
    "-deprecation"            // Emit warning and location for usages of deprecated APIs
  , "-encoding", "UTF-8"      // Specify character encoding used by source files
  , "-feature"                // Emit warning and location for usages of features that should be imported explicitly
  , "-target:jvm-1.8"         // Target platform for object files
  , "-unchecked"              // Enable additional warnings where generated code depends on assumptions
  , "-Xfatal-warnings"        // Fail the compilation if there are any warnings
  , "-Xlint:_,-nullary-unit"  // Enable or disable specific warnings (see list below)
  , "-Yno-adapted-args"       // Do not adapt an argument list to match the receiver
  , "-Ywarn-dead-code"        // Warn when dead code is identified
  , "-Ywarn-unused"           // Warn when local and private vals, vars, defs, and types are are unused
  , "-Ywarn-unused-import"    // Warn when imports are unused
  , "-Ywarn-value-discard"    // Warn when non-Unit expression results are unused
)
