package com.example

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.naoh.beef.Auth
import com.naoh.beef.Beef
import com.naoh.beef.Client
import com.naoh.beef.Region
import com.naoh.beef.Server
import com.naoh.beef.proto.echo.EchoGrpc
import com.naoh.beef.proto.echo.EchoReq
import com.naoh.beef.proto.echo.EchoRes

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object ApplicationMain extends App {
  val system = ActorSystem("MyActorSystem")
  val region = Region("rg")
  val auth = Auth("au")

  val serverCtx = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())


  Beef(system)(
    Server("rg")
      << EchoGrpc.bindService(EchoImpl, serverCtx))

  val clientCtx = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val builder = Client(region, auth, clientCtx) connect Beef(system)

  val client = builder.build(new EchoGrpc.EchoBlockingStub(_, _))

  println(s">> ${client.retEcho(EchoReq("hoovar"))}")

  clientCtx.shutdown()
  serverCtx.shutdown()
  Await.ready(system.terminate(), Duration.Inf)
  System.exit(0)
}

object EchoImpl extends EchoGrpc.Echo {
  override def retEcho(request: EchoReq): Future[EchoRes] =
    Future successful EchoRes(request.say.reverse)
}