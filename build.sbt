name := """akka-http-client"""

version := "1.0"

scalaVersion := "2.11.8"

// Uncomment to use Akka
//libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.11"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.0.6+22-2aae0b18+20170511-1652",
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.0-M3"
)

