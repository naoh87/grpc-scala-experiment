package com.naoh.beef.internal.server

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import com.naoh.beef.Server
import com.naoh.beef.internal.Network.SerializedActorRef
import com.naoh.beef.proto.stream.OnComplete
import com.naoh.beef.proto.stream.Start
import io.grpc.ServerMethodDefinition
import io.grpc.Status

/**
  * Created by naoh on 2017/01/03.
  */
object ServerChannelActor {
  def props(server: Server) = Props(new ServerChannelActor(server))
}

class ServerChannelActor(
  server: Server
) extends Actor {
  var current: ActorRef = _
  var handled = 0

  override def receive: Receive = {
    case start: Start =>
      start.ref match {
        case SerializedActorRef(ref) =>
          server.find(start.path) match {
            case Some(method) =>
              current = next(ref, method)
            case None =>
              ref ! OnComplete(Status.NOT_FOUND.getCode.value())
          }
      }
    case message =>
      current forward message
  }

  def next[ReqT, ResT](actorRef: ActorRef, method: ServerMethodDefinition[ReqT, ResT]): ActorRef = {
    handled += 1
    context.actorOf(ServerRequestHandler.props(actorRef, method), s"handler-$handled")
  }
}
