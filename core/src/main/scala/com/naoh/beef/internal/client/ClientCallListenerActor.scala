package com.naoh.beef.internal.client

import akka.actor.Actor
import akka.actor.Props
import com.naoh.beef.internal.Network
import com.naoh.beef.internal.Network.ProtoHeader
import com.naoh.beef.internal.Network.ProtoOnComplete
import com.naoh.beef.proto.{stream => ps}
import io.grpc.ClientCall.Listener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status

/**
  * Created by naoh on 2017/01/04.
  */

object ClientCallListenerActor {
  def props[ReqT, ResT](
    methodDescriptor: MethodDescriptor[ReqT, ResT],
    responseListener: Listener[ResT]
  ) = Props(new ClientCallListenerActor(responseListener, methodDescriptor))
}

class ClientCallListenerActor[ReqT, ResT](
  handler: Listener[ResT],
  methodDescriptor: MethodDescriptor[ReqT, ResT]
) extends Actor {
  override def receive: Receive = {
    case message: ps.Response =>
      val res = methodDescriptor.parseResponse(message.payload.newInput())
      handler.onMessage(res)
    case msg@ProtoHeader(meta) =>
      handler.onHeaders(meta)
    case msg@ProtoOnComplete(status, trailers, index) =>
      handler.onClose(status, trailers)
      context.parent ! ClientCallAgent.End
      context stop self
    case Network.NotFound(name) =>
      handler.onClose(Status.NOT_FOUND, new Metadata())
      context.parent ! ClientCallAgent.End
      context stop self
    case uk =>
      handler.onClose(Status.UNKNOWN, new Metadata())
      context.parent ! ClientCallAgent.End
      context stop self
  }
}
