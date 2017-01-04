package com.naoh.beef.internal.client

import com.naoh.beef.Beef
import com.naoh.beef.Client
import com.naoh.beef.Region
import com.naoh.beef.StubBuilder
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.MethodDescriptor
import io.grpc.stub.AbstractStub

class BeefStubBuilder(client: Client, beef: Beef) extends StubBuilder {
  private[this] val callOptions = CallOptions.DEFAULT.withExecutor(client.context)

  override def build[S <: AbstractStub[S]](f: (Channel, CallOptions) => S): S =
    f(new BeefChannel(beef, client), callOptions)

  override def region: Region = client.region
}

class BeefChannel(
  beef: Beef,
  client: Client
) extends Channel {

  override def newCall[RequestT, ResponseT](
    methodDescriptor: MethodDescriptor[RequestT, ResponseT],
    callOptions: CallOptions
  ): ClientCall[RequestT, ResponseT] = {
    val agent = beef.system.actorOf(ClientCallAgent.props(client.region, beef.network, methodDescriptor), client.auth.value)
    new BeefClientCall(client.region, agent)(methodDescriptor)
  }

  override def authority(): String = client.auth.value
}