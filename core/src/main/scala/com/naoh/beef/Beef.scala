package com.naoh.beef

import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import com.naoh.beef.internal.Network
import com.naoh.beef.internal.client.BeefStubBuilder
import com.naoh.beef.internal.server.ServerActor
import io.grpc.ServerMethodDefinition
import io.grpc.ServerServiceDefinition
import io.grpc.stub.AbstractStub

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor


class Beef(val system: ExtendedActorSystem) extends Extension {
  val network = system.actorOf(Network.props, "beef-network")

  def apply(builder: ServerBuilder): Beef = {
    val server = builder.build()
    system.actorOf(ServerActor.props(server), server.region.name)
    this
  }

  def provideBuilder(client: Client): StubBuilder = new BeefStubBuilder(client, this)
}

object Beef extends ExtensionId[Beef] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): Beef = new Beef(system)

  override def lookup(): ExtensionId[_ <: Extension] = Beef
}

case class Region(name: String)

class Server(
  val region: Region,
  methods: Map[String, ServerMethodDefinition[_, _]]
) {
  def find(path: String): Option[ServerMethodDefinition[_, _]] =
    methods.get(path)
}

case class ServerBuilder(
  region: Region,
  services: Seq[ServerServiceDefinition] = Seq.empty
) {
  def <<(service: ServerServiceDefinition*): ServerBuilder =
    this.copy(services = service ++ services)

  import scala.collection.JavaConverters._

  def build(): Server = new Server(region, buildMethods())

  private def buildMethods(): Map[String, ServerMethodDefinition[_, _]] = {
    for {
      service <- services
      method <- service.getMethods.asScala
    } yield method.getMethodDescriptor.getFullMethodName -> method
  }.toMap
}

object Server {
  def apply(name: String): ServerBuilder = ServerBuilder(Region(name))
}

case class Auth(value: String)

case class Client(
  region: Region,
  auth: Auth,
  context: ExecutionContextExecutor
) {
  def connect(beef: Beef): StubBuilder = beef.provideBuilder(this)
}

trait StubBuilder {
  def region: Region

  def build[S <: AbstractStub[S]](f: (_root_.io.grpc.Channel, _root_.io.grpc.CallOptions) => S): S
}
