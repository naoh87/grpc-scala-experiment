package com.naoh.beef.internal

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.serialization.SerializationExtension
import com.naoh.beef.proto.network.RequestChannel

/**
  * Created by naoh on 2017/01/03.
  */
class ServerChannelActor(selector: ActorRef, name: String) extends Actor {

  import Network._

  implicit val extendedSystem = SerializationExtension(context.system).system

  val pending = ScalaDeque.empty[ActorRef]
  val buffer = ScalaDeque.empty[ServerChannel]

  override def receive: Receive = {
    case msg@Open(`name`) =>
      buffer.poll.fold[Unit] {
        pending add sender()
        selector ! ServerSelectorActor.Find(name)
      } { channel =>
        sender() ! channel
      }
    case ch: ServerChannel =>
      pending.poll.fold[Unit](
        buffer add ch
      ) { ref =>
        ref ! ch
      }
    case ProtoServerChannel(ch) =>
      self forward ch
    case msg@ServerLocation(region, ref) =>
      ref ! RequestChannel(name)
    case NotFound(`name`) =>
      pending.poll.foreach(_ ! NotFound(name))
    case msg =>
  }

}

object ServerChannelActor {
  def props(selector: ActorRef, name: String) = Props(new ServerChannelActor(selector, name))

}