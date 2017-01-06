package com.example

import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.naoh.beef.Auth
import com.naoh.beef.Beef
import com.naoh.beef.Client
import com.naoh.beef.Region
import com.naoh.beef.Server
import com.naoh.beef.proto.echo.EchoGrpc
import com.naoh.beef.proto.echo.EchoReq
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * Created by naoh on 2017/01/07.
  */
class BeefTest {

  val serverSystem = ActorSystem("MyActorSystem", ConfigFactory.parseResources("server.conf").resolve())
  val clientSystem = ActorSystem("MyActorSystem", ConfigFactory.parseResources("client.conf").resolve())
  Cluster(serverSystem).join(Cluster(serverSystem).selfAddress)
  Cluster(clientSystem).join(Cluster(serverSystem).selfAddress)
  val serverCtx = ExecutionContext.fromExecutorService(Executors.newScheduledThreadPool(8))
  val clientCtx = ExecutionContext.fromExecutorService(Executors.newScheduledThreadPool(8))

  val region = Region("rg")
  val auth = Auth("au")

  Thread.sleep(1000)

  Beef(serverSystem)(
    Server(region)
      << EchoGrpc.bindService(EchoImpl, serverCtx))
  Thread.sleep(1000)

  val builder = Client(region, auth, clientCtx) connect Beef(clientSystem)
  val client = builder.build(new EchoGrpc.EchoBlockingStub(_, _))
  Thread.sleep(1000)

  val base = Instant.now
  Iterator.range(0, 3000).toSeq.toParArray.foreach{_ => Try(client.retEcho(EchoReq("12"))); print(".")}
  val record = Duration.between(base, Instant.now())

  println(s"\n\nDuration $record \n")

  Thread.sleep(2000)
  clientCtx.shutdown()
  serverCtx.shutdown()
  clientSystem.shutdown()
  serverSystem.shutdown()
}
