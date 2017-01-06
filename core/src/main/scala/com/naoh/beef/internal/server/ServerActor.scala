package com.naoh.beef.internal.server

import akka.actor.Actor
import akka.actor.Props
import akka.serialization.Serialization
import com.naoh.beef.Beef
import com.naoh.beef.Server
import com.naoh.beef.internal.Network.ServerLocation
import com.naoh.beef.internal.ServerSelectorActor
import com.naoh.beef.proto.network.RequestChannel
import com.naoh.beef.proto.network.ServerChannel

/**
  * Created by naoh on 2017/01/03.
  */

object ServerActor {
  def props(server: Server) = Props(new ServerActor(server))
}

class ServerActor(
  server: Server
) extends Actor {
  val location = ServerLocation(server.region, self)
  val locationProto = location.toProto
  Beef(context.system).network ! ServerSelectorActor.Add(location)

  println("Server Actor Start")
  var created = 0

  override def receive: Receive = {
    case req: RequestChannel =>
      sender() ! create()
  }

  def create(): ServerChannel = {
    val ref = context.actorOf(ServerChannelActor.props(server), s"channel-$created")
    created += 1
    ServerChannel(Some(location.toProto), Serialization.serializedActorPath(ref))
  }
}



