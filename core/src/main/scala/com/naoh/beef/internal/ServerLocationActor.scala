package com.naoh.beef.internal

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.serialization.SerializationExtension
import com.naoh.beef.proto.network.RequestChannel

/**
  * Created by naoh on 2017/01/03.
  */
class ServerLocationActor(name: String) extends Actor {

  import Network._

  implicit val extendedSystem = SerializationExtension(context.system).system

  val pending = ScalaDeque.empty[ActorRef]
  val buffer = ScalaDeque.empty[ServerChannel]

  var servers = Set.empty[ServerLocation]
  var rounds = servers.toList
  var index = 0

  override def receive: Receive = {
    case msg@Open(`name`) =>
      find().fold(
        sender() ! Network.NotFound(name)
      ) { server =>
        server.ref ! RequestChannel(name)
        pending add sender()
      }
    case ch: ServerChannel =>
      pending.poll.fold[Unit](
        buffer add ch
      ) { ref =>
        ref ! ch
      }
    case ProtoServerChannel(channel) =>
      self forward channel
    case ServerJoins(server) =>
      server.ref ! RequestChannel(name)
      add(server)
    case ServerLeft(server) =>
      remove(server)
  }

  def find(): Option[ServerLocation] = {
    if (servers.isEmpty) {
      None
    } else {
      index = (index + 1) % rounds.size
      Some(rounds(index))
    }
  }

  def add(server: ServerLocation): Unit = {
    servers += server
    reallocate()
  }

  def remove(server: ServerLocation): Unit = {
    servers -= server
    reallocate()
  }

  def reallocate() = {
    rounds = servers.toList
  }
}

object ServerLocationActor {
  def props(name: String) = Props(new ServerLocationActor(name))

}