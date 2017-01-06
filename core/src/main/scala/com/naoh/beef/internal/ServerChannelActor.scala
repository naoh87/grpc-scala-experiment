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
        println(s"CA: Go pending: ${sender()}")
        pending add sender()
        selector ! ServerSelectorActor.Find(name)
      } { channel =>
        println("CA: found")
        sender() ! channel
      }
    case ch: ServerChannel =>
      println(s"CA: Recv CC ${ch}")
      pending.poll.fold[Unit](
        buffer add ch
      ) { ref =>
        ref ! ch
      }
    case ProtoServerChannel(ch) =>
      self forward ch
    case msg@ServerLocation(region, ref) =>
      println(s"CA: Recv SL ${msg}")
      ref ! RequestChannel(name)
    case NotFound(`name`) =>
      println(s"CA: 404")
      pending.poll.foreach(_ ! NotFound(name))
    case msg =>
      println(s"CA: unhandled $msg, ${msg.getClass}")
  }

}

object ServerChannelActor {
  def props(selector: ActorRef, name: String) = Props(new ServerChannelActor(selector, name))

}