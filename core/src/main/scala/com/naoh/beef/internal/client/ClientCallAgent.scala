package com.naoh.beef.internal.client

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.actor.SupervisorStrategy.Stop
import com.naoh.beef.Region
import com.naoh.beef.internal.Network
import com.naoh.beef.internal.Network.Open
import com.naoh.beef.internal.Network.ProtoRequest
import com.naoh.beef.internal.Network.SerializedActorRef
import com.naoh.beef.internal.Network.ServerChannel
import com.naoh.beef.internal.ScalaDeque
import com.naoh.beef.internal.client.ClientCallAgent.RequestStart
import com.naoh.beef.proto.{stream => ps}
import io.grpc.ClientCall
import io.grpc.ClientCall.Listener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status

/**
  * Created by naoh on 2017/01/04.
  */
class BeefClientCall[ReqT, ResT](
  region: Region,
  agent: ActorRef
)(implicit
  methodDescriptor: MethodDescriptor[ReqT, ResT]
) extends ClientCall[ReqT, ResT] {
  override def cancel(message: String, cause: Throwable): Unit =
    agent ! ps.Cancel(Status.ABORTED.getCode.value())

  override def halfClose(): Unit =
    agent ! ps.HalfClose(0)

  override def request(numMessages: Int): Unit = {
  }

  override def sendMessage(message: ReqT): Unit = {
    agent ! ProtoRequest(message, 0)
  }

  override def start(responseListener: Listener[ResT], headers: Metadata): Unit =
    agent ! RequestStart(methodDescriptor.getFullMethodName, responseListener)
}

object ClientCallAgent {
  def props[ReqT, ResT](
    region: Region,
    transport: ActorRef,
    methodDescriptor: MethodDescriptor[ReqT, ResT]) =
    Props(new ClientCallAgent(region, transport, methodDescriptor))

  case class RequestStart[ResT](method: String, responseListener: Listener[ResT])

  case object End

}

class ClientCallAgent[ReqT, ResT](
  region: Region,
  transport: ActorRef,
  methodDescriptor: MethodDescriptor[ReqT, ResT]
) extends Actor {
  val queue = ScalaDeque.empty[Any]
  var listener: ActorRef = _
  var remote: ActorRef = _


  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ => Stop
  }

  override def receive: Receive = {
    case msg@RequestStart(method, handler) =>
      println("CC: RqStart")
      this.listener = context.actorOf(ClientCallListenerActor.props(methodDescriptor, handler.asInstanceOf[Listener[ResT]]), "listener")
      transport ! Open(region.name)
      queue << ps.Start(method, SerializedActorRef(this.listener))
    case msg@ServerChannel(ref, serer) =>
      println(s"CC: Recv $msg")
      this.remote = ref
      context become online
      queue.polls(online.lift)
    case msg@Network.NotFound(name) =>
      println(s"CC: NotFound $msg")
      listener ! msg
    case ClientCallAgent.End =>
      context stop self
    case msg =>
      println(s"CC: queue $msg")
      queue << msg
  }

  def online: Receive = {
    case msg: ps.HalfClose =>
      this.remote ! msg
    case ClientCallAgent.End =>
      context stop self
      transport ! remote
    case msg =>
      this.remote ! msg
  }
}


