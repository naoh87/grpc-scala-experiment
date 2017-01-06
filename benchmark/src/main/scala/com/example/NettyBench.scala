package com.example

import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

import com.naoh.beef.Client
import com.naoh.beef.Server
import com.naoh.beef.proto.echo.EchoGrpc
import com.naoh.beef.proto.echo.EchoGrpc.EchoBlockingStub
import com.naoh.beef.proto.echo.EchoReq
import io.grpc.CallOptions
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * Created by naoh on 2017/01/07.
  */
class NettyBench {

  val serverCtx = ExecutionContext.fromExecutorService(Executors.newScheduledThreadPool(8))
  val clientCtx = ExecutionContext.fromExecutorService(Executors.newScheduledThreadPool(8))
  val server = NettyServerBuilder.forPort(8899).addService(EchoGrpc.bindService(EchoImpl, serverCtx)).build().start()
  val ch = NettyChannelBuilder.forAddress("localhost", 8899).usePlaintext(true).build()
  val client = new EchoBlockingStub(ch, CallOptions.DEFAULT.withExecutor(clientCtx))
  Thread.sleep(1000)

  val base = Instant.now
  Iterator.range(0, 3000).toSeq.toParArray.foreach { _ => Try(client.retEcho(EchoReq("12"))); print(".") }
  val record = Duration.between(base, Instant.now())

  println(s"\n\nDuration $record \n")

  Thread.sleep(2000)
  clientCtx.shutdown()
  serverCtx.shutdown()
  server.shutdown()
  ch.shutdown()
}
