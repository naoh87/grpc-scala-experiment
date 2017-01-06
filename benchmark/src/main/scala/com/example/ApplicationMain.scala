package com.example

import java.time.{Duration => jDuration}

import com.naoh.beef.proto.echo.EchoGrpc
import com.naoh.beef.proto.echo.EchoReq
import com.naoh.beef.proto.echo.EchoRes

import scala.concurrent.Future

object ApplicationMain extends App {

  val netty = new NettyBench().record
  Thread.sleep(1000)
  val beef = new BeefTest().record
}


object EchoImpl extends EchoGrpc.Echo {
  override def retEcho(request: EchoReq): Future[EchoRes] =
    Future successful EchoRes(request.say.reverse)
}