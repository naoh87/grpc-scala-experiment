lazy val commonSettings = Seq(
  organization := "com.github.naoh87",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)

lazy val root = (project in file(".")).
  aggregate(core, benchmark).
  settings(aggregate in update := false)

lazy val core = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(name:= "beef")
  .settings(
    libraryDependencies ++= {
      val akka = "2.4.16"
      val scalatest = "3.0.1"
      val grpc = "1.0.1"
      val common_io = "2.5"
      Seq(
        "com.typesafe.akka" %% "akka-actor" % akka,
        "com.typesafe.akka" %% "akka-agent" % akka,
        "com.typesafe.akka" %% "akka-cluster" % akka,
        "com.typesafe.akka" %% "akka-cluster-tools" % akka,
        "com.typesafe.akka" %% "akka-testkit" % akka % "test",

        "io.grpc" % "grpc-core" % grpc,
        "commons-io" % "commons-io" % common_io,

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
  .dependsOn(core % "compile->compile")
  .settings(
    PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value)
  )
