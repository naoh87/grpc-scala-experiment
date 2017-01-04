package com.naoh.beef.internal.server

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import com.naoh.beef.proto.{stream => ps}
import io.grpc.Metadata
import io.grpc.ServerCall.Listener
import io.grpc.ServerMethodDefinition
import io.grpc.Status

/**
  * Created by naoh on 2017/01/03.
  */

object ServerRequestHandler {
  def props[ReqT, ResT](
    remote: ActorRef,
    methodDefinition: ServerMethodDefinition[ReqT, ResT]
  ) = Props(new ServerRequestHandler[ReqT, ResT](remote)(methodDefinition))
}

class ServerRequestHandler[ReqT, ResT](
  remote: ActorRef
)(
  implicit methodDef: ServerMethodDefinition[ReqT, ResT]
) extends Actor {

  val agent = context actorOf ServerCallAgent.props[ReqT, ResT](remote)
  val serverCall = new ServerCallHandler[ReqT, ResT](agent)(methodDef.getMethodDescriptor)
  val handler: Listener[ReqT] =
    methodDef.getServerCallHandler.startCall(serverCall, new Metadata())

  override def receive: Receive = {
    case req: ps.Request =>
      handler.onMessage(
        methodDef.getMethodDescriptor
          .parseRequest(req.payload.newInput()))
    case ps.HalfClose(index) =>
      handler.onHalfClose()
      agent ! handler
    case msg =>
      handler.onCancel()
      agent ! ps.OnComplete(Status.ABORTED.getCode.value())
      context stop self
  }
}

