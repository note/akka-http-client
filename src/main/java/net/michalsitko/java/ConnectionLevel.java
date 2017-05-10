package net.michalsitko.java;

import akka.actor.ActorSystem;
import akka.http.javadsl.ClientTransport;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.OutgoingConnection;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.settings.ClientConnectionSettings;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

class ConnectionLevelClient {

    private final ActorSystem system;
    private final Materializer materializer;
    private final Flow<HttpRequest, HttpResponse, CompletionStage<OutgoingConnection>> connectionFlow;

    public ConnectionLevelClient(ActorSystem sys, Materializer mat) {
        system = sys;
        materializer = mat;

        InetSocketAddress addr = new InetSocketAddress("localhost", 8888);
        ClientTransport clientTransport = ClientTransport.proxy(Optional.empty(), addr, ClientConnectionSettings.create(system));
        ConnectionPoolSettings settings = ConnectionPoolSettings.create(system).withTransport(clientTransport);
        // TODO: Cannot pass settings to outgoingConnection
        connectionFlow = Http.get(system).outgoingConnection(ConnectHttp.toHost("www.scala-lang.org", 443));
    }

    public Materializer getMaterializer() {
        return materializer;
    }

    public void close() {
        System.out.println("Shutting down client");
        Http.get(system).shutdownAllConnectionPools().whenComplete((s, f) -> system.terminate());
    }

    private Uri uri = Uri.create("https://www.scala-lang.org/resources/css/prettify.css");

    public <U> CompletionStage<U> connectionLevel(Function<HttpResponse, CompletionStage<U>> responseHandler) {
        return Source.single(HttpRequest.create().withUri(uri))
                .via(connectionFlow)
                .runWith(Sink.head(), materializer)
                .thenComposeAsync(responseHandler);
    }

}

public class ConnectionLevel {
    public static void main(String[] args) {
        System.out.println("Starting...");
        final ActorSystem system = ActorSystem.create();
        final Materializer materializer = ActorMaterializer.create(system);

        ConnectionLevelClient client = new ConnectionLevelClient(system, materializer);
        client.connectionLevel(success -> {
                    System.out.println("some result");
                    return success.entity()
                            .getDataBytes()
                            .runForeach(byteString -> System.out.println("Result: " + byteString.utf8String()),
                                    client.getMaterializer());
                }
        ).whenComplete((success, throwable) -> client.close());
    }
}
