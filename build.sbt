lazy val commonSettings = Seq(
  organization := "com.github.naoh87",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)
val akka = "2.4.16"
val scalatest = "3.0.1"
val grpc = "1.0.1"
val common_io = "2.5"

lazy val root = (project in file(".")).
  aggregate(core, benchmark).
  settings(aggregate in update := false)

lazy val core = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(name:= "beef")
  .settings(
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka" %% "akka-actor" % akka,
        "com.typesafe.akka" %% "akka-agent" % akka,
        "com.typesafe.akka" %% "akka-cluster" % akka,
        "com.typesafe.akka" %% "akka-cluster-tools" % akka,
        "com.typesafe.akka" %% "akka-testkit" % akka % "test",

        "io.grpc" % "grpc-core" % grpc,

        "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf",
        "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % com.trueaccord.scalapb.compiler.Version.scalapbVersion,

        "org.scalatest" %% "scalatest" % scalatest % "test"
      )
    }
  ).settings(
    PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value),
    publishTo := Some(Resolver.file("file",  new File( "releases" )) )
  )


lazy val benchmark = (project in file("benchmark"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= {
      Seq(
        "io.grpc" % "grpc-netty" % grpc
      )
    }
  )
  .settings(
    PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value)
  )
  .dependsOn(core % "compile->compile")
