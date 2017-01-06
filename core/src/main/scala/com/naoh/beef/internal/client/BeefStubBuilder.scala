package com.naoh.beef.internal.client

import java.util.Base64
import java.util.concurrent.TimeUnit

import com.naoh.beef.Beef
import com.naoh.beef.Client
import com.naoh.beef.Region
import com.naoh.beef.StubBuilder
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.Deadline
import io.grpc.MethodDescriptor
import io.grpc.stub.AbstractStub

import scala.util.Random

class BeefStubBuilder(client: Client, beef: Beef) extends StubBuilder {
  private[this] val callOptions = CallOptions.DEFAULT.withExecutor(client.context)

  override def build[S <: AbstractStub[S]](f: (Channel, CallOptions) => S): S =
    f(new BeefChannel(beef, client), callOptions.withDeadline(Deadline.after(3, TimeUnit.SECONDS)))

  override def region: Region = client.region
}

class BeefChannel(
  beef: Beef,
  client: Client
) extends Channel {


  def uuid: String = {
    Random.nextLong().toHexString
  }

  override def newCall[RequestT, ResponseT](
    methodDescriptor: MethodDescriptor[RequestT, ResponseT],
    callOptions: CallOptions
  ): ClientCall[RequestT, ResponseT] = {
    val agent = beef.system.actorOf(ClientCallAgent.props(client.region, beef.network, methodDescriptor), client.auth.value+s"-$uuid")
    new BeefClientCall(client.region, agent)(methodDescriptor)
  }

  override def authority(): String = client.auth.value
}