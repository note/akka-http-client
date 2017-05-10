package net.michalsitko.java;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.ClientTransport;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.HostConnectionPool;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.http.javadsl.settings.ClientConnectionSettings;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

import scala.util.Try;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

class HostLevelClient {

    private final ActorSystem system;
    private final Materializer materializer;
    private final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> poolClientFlow;

    public HostLevelClient(ActorSystem sys, Materializer mat) {
        system = sys;
        materializer = mat;

        InetSocketAddress addr = new InetSocketAddress("localhost", 8888);
        ClientTransport clientTransport = ClientTransport.proxy(Optional.empty(), addr, ClientConnectionSettings.create(system));
        ConnectionPoolSettings settings = ConnectionPoolSettings.create(system).withTransport(clientTransport);
        poolClientFlow = Http.get(system).cachedHostConnectionPool(
                ConnectHttp.toHost("www.scala-lang.org", 443), settings, system.log(), materializer);
    }

    public ActorSystem getSystem() {
        return system;
    }

    public Materializer getMaterializer() {
        return materializer;
    }

    public void close() {
        System.out.println("Shutting down client");
        Http.get(system).shutdownAllConnectionPools().whenComplete((s, f) -> system.terminate());
    }

    private Uri uri = Uri.create("https://www.scala-lang.org/resources/css/prettify.css");

    public <U> CompletionStage<U> hostLevel(Function<Pair<Try<HttpResponse>, Integer>, CompletionStage<U>> responseHandler) {
        return Source.single(Pair.create(HttpRequest.create().withUri(uri), 42))
                .via(poolClientFlow)
                .runWith(Sink.head(), materializer)
                .thenComposeAsync(responseHandler);
    }

}

public class HostLevel {
    public static void main(String[] args) {
        System.out.println("Starting...");
        final ActorSystem system = ActorSystem.create();
        final Materializer materializer = ActorMaterializer.create(system);

        HostLevelClient client = new HostLevelClient(system, materializer);
        client.hostLevel(success ->
                success.first().get().entity()
                        .getDataBytes()
                        .runFold(ByteString.empty(), ByteString::concat, client.getMaterializer())
                        .handle((byteString, f) -> {
                            if (f != null) {
                                System.err.println("Failure when handle the HttpEntity: " + f.getMessage());
                            } else {
                                System.out.println("Result: " + byteString.utf8String());
                            }
                            return NotUsed.getInstance();
                        })
        ).whenComplete((success, throwable) -> client.close());
    }
}

