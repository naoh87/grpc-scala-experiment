package com.naoh.beef.internal

import java.time.Duration
import java.time.Instant

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import akka.serialization.SerializationExtension
import com.naoh.beef.internal.Network.ServerLocation
import com.naoh.beef.proto.{network => pn}

import scala.collection.JavaConverters._

class ServerSelectorActor(name: String) extends Actor with ActorLogging {

  import Network._

  val serverTopic = name + "#asServer"

  implicit val extendedSystem = SerializationExtension(context.system).system
  var mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(name, self)

  val serverMode = Cluster(context.system).getSelfRoles.asScala.contains("server")
  if(serverMode){
    log.info(s"\nAs Server ${SerializedActorRef(self)}")
    mediator ! Subscribe(serverTopic, self)
  } else {
    log.info(s"\nAs Client ${SerializedActorRef(self)}")
  }

  @volatile
  var locals = Set.empty[ServerLocation]

  @volatile
  var servers = Set.empty[ServerLocation]

  @volatile
  var rounds = servers.toList
  var idx = 0
  val basetime = Instant.now()

  def timestamp = s"$serverMode %3d".format(Duration.between(basetime, Instant.now).getNano / 1000000)

  override def receive: Receive = {
    case ServerSelectorActor.Find(`name`) =>
      sender() ! shuffle.getOrElse(NotFound(name))
    case ServerSelectorActor.Add(server) =>
      locals = locals + server
      reallocate()
      mediator ! Publish(name, ProtoServerJoin(server))
    case ProtoServerJoin(server) =>
      if (server.region.name == name){
        servers += server
        reallocate()
      }
    case ProtoServerLeft(server) =>
      servers -= server
      reallocate()
    case SubscribeAck(Subscribe(topic, None, _)) =>
      locals.foreach(location => mediator ! ProtoServerJoin(location))
      mediator ! Publish(serverTopic, pn.ServerFetch(SerializedActorRef(self)))
    case pn.ServerFetch(SerializedActorRef(ref)) if locals.nonEmpty && ref != self =>
      locals.foreach(location => ref ! ProtoServerJoin(location))
  }

  def shuffle: Option[ServerLocation] = {
    if (rounds.isEmpty)
      None
    else {
      idx = (idx + 1) % rounds.size
      Some(rounds(idx))
    }
  }

  def reallocate() = {
    rounds = servers.toList
  }
}

object ServerSelectorActor {

  def props(name: String) = Props(new ServerSelectorActor(name))

  case class Add(server: ServerLocation)

  case class Find(name: String)

}