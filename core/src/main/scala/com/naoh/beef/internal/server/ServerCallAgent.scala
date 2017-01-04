package com.naoh.beef.internal.server

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import com.google.protobuf.ByteString
import com.naoh.beef.internal.Network.ProtoMeta
import com.naoh.beef.internal.Network.ProtoResponse
import com.naoh.beef.proto.stream.Header
import com.naoh.beef.proto.stream.OnComplete
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.Status


class ServerCallHandler[ReqT, ResT](
  agent: ActorRef
)(
  implicit methodDesc: MethodDescriptor[ReqT, ResT]
) extends ServerCall[ReqT, ResT] {
  var cancelled = false

  override def isCancelled: Boolean = cancelled

  override def sendHeaders(headers: Metadata): Unit = agent ! Header(ProtoMeta(headers))

  override def getMethodDescriptor: MethodDescriptor[ReqT, ResT] = methodDesc

  override def close(status: Status, trailers: Metadata): Unit =
    agent ! OnComplete(1, status.getCode.value(), ProtoMeta(trailers))

  override def request(numMessages: Int): Unit = {
  }

  override def sendMessage(message: ResT): Unit = {
    agent ! ProtoResponse(message, 1)
  }
}

object ServerCallAgent {
  def props[ReqT, ResT](
    remote: ActorRef
  ) = Props(new ServerCallAgent[ReqT, ResT](remote))
}

class ServerCallAgent[ReqT, ResT](
  remote: ActorRef
) extends Actor {
  var completed = false
  var handler: Listener[ReqT] = _

  override def receive: Receive = waitingHandler orElse {
    case msg: OnComplete =>
      remote ! msg
      completed = true
      Option(handler).foreach(_.onComplete())
    case msg =>
      remote ! msg
  }

  def waitingHandler: Receive = {
    case handler: Listener[ReqT] =>
      this.handler = handler
      if (completed) handler.onComplete()
  }
}