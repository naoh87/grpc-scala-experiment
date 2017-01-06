package com.naoh.beef.internal

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ExtendedActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.serialization._
import com.google.protobuf.ByteString
import com.naoh.beef.Region
import com.naoh.beef.proto.{network => pn}
import com.naoh.beef.proto.{stream => ps}
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerMethodDefinition
import io.grpc.Status

import scala.collection.mutable
import scala.concurrent.ExecutionContext

/**
  * Created by naoh on 2017/01/03.
  */
class Network extends Actor {

  import Network._

  implicit val extendedSystem = SerializationExtension(context.system).system
  val actives = mutable.HashMap.empty[String, Hub]

  implicit def ctx: ExecutionContext = context.system.dispatcher

  override def receive: Receive = {
    case msg@ServerSelectorActor.Add(server) =>
      find(server.region.name).server ! msg
    case msg: pn.ServerJoin =>
      msg.server
        .map(_.region)
        .map(find)
        .foreach(_.server ! msg)
    case msg: pn.ServerLeave =>
      msg.server
        .map(_.region)
        .map(find)
        .foreach(_.server ! msg)
    case msg@Open(name) =>
      find(name).channel forward msg
    case Prepare(Region(name)) =>
      find(name)
  }

  def find(name: String): Hub = actives.getOrElseUpdate(name, create(name))

  def create(name: String): Hub = {
    val server = context.actorOf(ServerSelectorActor.props(name), s"selector-$name")
    val channel = context.actorOf(ServerChannelActor.props(server, name), s"locator-$name")
    Hub(server, channel)
  }
}


object Network {

  case class Hub(server: ActorRef, channel: ActorRef)

  case class Prepare(region: Region)

  val props = Props[Network]

  case class ServerLocation(region: Region, ref: ActorRef) {
    def toProto: pn.ServerLocation =
      pn.ServerLocation(region.name, Serialization.serializedActorPath(ref))
  }

  case class Open(name: String)

  case class NotFound(name: String)

  case class ServerChannel(actorRef: ActorRef, server: ServerLocation)

  object ProtoServerJoin {
    def apply(server: ServerLocation): pn.ServerJoin =
      pn.ServerJoin(Some(ProtoServer(server)))

    def unapply(join: pn.ServerJoin)(implicit extendedSystem: ExtendedActorSystem): Option[ServerLocation] = {
      join.server.flatMap(ProtoServer.unapply)
    }
  }

  object ProtoServerLeft {
    def unapply(leave: pn.ServerLeave)(implicit extendedSystem: ExtendedActorSystem): Option[ServerLocation] = {
      leave.server.flatMap(ProtoServer.unapply)
    }
  }

  object ProtoServer {
    def apply(serverLocation: ServerLocation): pn.ServerLocation =
      pn.ServerLocation(serverLocation.region.name, Serialization.serializedActorPath(serverLocation.ref))
    def unapply(server: pn.ServerLocation)(implicit extendedSystem: ExtendedActorSystem): Option[ServerLocation] = {
      Some(ServerLocation(Region(server.region), extendedSystem.provider.resolveActorRef(server.ref)))
    }
  }


  object SerializedActorRef {
    def apply(actorRef: ActorRef): String = {

      val ref = Serialization.serializedActorPath(actorRef)
      println(s">>Serizlied $ref")
      ref
    }

    def unapply(ref: String)(implicit context: ActorContext): Option[ActorRef] =
      Some(SerializationExtension(context.system).system.provider.resolveActorRef(ref))
  }

  object ProtoMeta {
    def apply(meta: Metadata): Seq[ByteString] = meta.serialize().map(ByteString.copyFrom)

    def unapply(msg: Seq[ByteString]): Option[Metadata] =
      Some(new Metadata(msg.map(_.toByteArray): _*))
  }

  object ProtoHeader {
    def unapply(header: ps.Header): Option[Metadata] = {
      ProtoMeta.unapply(header.meta)
    }
  }

  object ProtoOnComplete {
    def unapply(message: ps.OnComplete): Option[(Status, Metadata, Int)] =
      for {
        meta <- ProtoMeta.unapply(message.trailers)
      } yield (Status.fromCodeValue(message.status), meta, message.index)
  }

  object ProtoServerChannel {
    def apply(channel: ServerChannel): pn.ServerChannel =
      pn.ServerChannel(Some(channel.server.toProto), SerializedActorRef(channel.actorRef))

    def unapply(arg: pn.ServerChannel)(implicit extendedSystem: ExtendedActorSystem): Option[ServerChannel] = {
      for {
        server <- arg.server.flatMap(ProtoServer.unapply)
      } yield ServerChannel(extendedSystem.provider.resolveActorRef(arg.ref), server)
    }
  }

  object ProtoRequest {
    def apply[ReqT, ResT](request: ReqT, index: Int)(
      implicit methodDef: MethodDescriptor[ReqT, ResT]
    ): ps.Request = ps.Request(ByteString.readFrom(methodDef.streamRequest(request)), index)

    def unapply[ReqT, ResT](
      request: ps.Request
    )(
      implicit methodDef: ServerMethodDefinition[ReqT, ResT]
    ): Option[(ReqT, Int)] = {
      Some(methodDef.getMethodDescriptor.parseRequest(request.payload.newInput()), request.index)
    }
  }

  object ProtoResponse {
    def apply[ReqT, ResT](response: ResT, index: Int)(
      implicit methodDef: MethodDescriptor[ReqT, ResT]
    ): ps.Response = ps.Response(ByteString.readFrom(methodDef.streamResponse(response)), index)

    def unapply[ReqT, ResT](
      response: ps.Response
    )(
      implicit methodDef: ServerMethodDefinition[ReqT, ResT]
    ): Option[(ResT, Int)] = {
      Some(methodDef.getMethodDescriptor.parseResponse(response.payload.newInput()), response.index)
    }
  }

}