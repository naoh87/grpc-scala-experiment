package com.example

import java.time.Instant
import java.time.{Duration => jDuration}
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
import com.naoh.beef.proto.echo.EchoRes
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

object ApplicationMain extends App {
  val serverSystem = ActorSystem("MyActorSystem", ConfigFactory.parseResources("server.conf").resolve())
  val clientSystem = ActorSystem("MyActorSystem", ConfigFactory.parseResources("client.conf").resolve())
  Cluster(serverSystem).join(Cluster(serverSystem).selfAddress)
  Cluster(clientSystem).join(Cluster(serverSystem).selfAddress)
  val serverCtx = ExecutionContext.fromExecutorService(Executors.newScheduledThreadPool(4))
  val clientCtx = ExecutionContext.fromExecutorService(Executors.newScheduledThreadPool(4))

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

  println("run-thing")

  println(s"\n\n>> ${Try(client.retEcho(EchoReq("hoovar")))}\n\n")
  Thread.sleep(1000)
  println(s"\n\n>> ${Try(client.retEcho(EchoReq("12345")))}\n\n")

  val base = Instant.now
  Iterator.range(0, 10000).toSeq.toParArray.foreach(_ => Try(client.retEcho(EchoReq("12"))))
  println(s"Duration ${jDuration.between(base, Instant.now())}")

  Thread.sleep(2000)
  clientCtx.shutdown()
  serverCtx.shutdown()
  Await.ready(clientSystem.terminate(), Duration.Inf)
  Await.ready(serverSystem.terminate(), Duration.Inf)
}

object EchoImpl extends EchoGrpc.Echo {
  override def retEcho(request: EchoReq): Future[EchoRes] =
    Future successful EchoRes(request.say.reverse)
}