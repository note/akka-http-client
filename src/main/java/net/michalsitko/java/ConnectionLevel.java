package net.michalsitko.java;

import akka.actor.ActorSystem;
import akka.http.javadsl.*;
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
import net.michalsitko.scala.utils.Config;

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

        InetSocketAddress addr = new InetSocketAddress(Config.proxyHost(), Config.proxyPort());
        ClientTransport clientTransport = ClientTransport.proxy(Optional.empty(), addr, ClientConnectionSettings.create(system));
        ConnectionPoolSettings settings = ConnectionPoolSettings.create(system).withTransport(clientTransport);
        ConnectHttp connectTo = new ConnectHttpsImpl("www.scala-lang.org", 443, Optional.empty());
        connectionFlow = Http.get(system).outgoingConnectionUsingTransport(connectTo, clientTransport, ClientConnectionSettings.create(system), system.log());
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
