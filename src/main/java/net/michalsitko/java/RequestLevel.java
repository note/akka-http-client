package net.michalsitko.java;

import akka.actor.ActorSystem;
import akka.http.javadsl.ClientTransport;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.settings.ClientConnectionSettings;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

class RequestLevelClient {

    private final ActorSystem system;
    private final Materializer materializer;

    public RequestLevelClient(ActorSystem sys, Materializer mat) {
        system = sys;
        materializer = mat;
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

    public <U> CompletionStage<U> requestLevelFutureBased(Function<HttpResponse, CompletionStage<U>> responseHandler) {
        InetSocketAddress addr = new InetSocketAddress("localhost", 8888);
        ClientTransport clientTransport = ClientTransport.proxy(Optional.empty(), addr, ClientConnectionSettings.create(system));
        ConnectionPoolSettings settings = ConnectionPoolSettings.create(system).withTransport(clientTransport);
        return Http.get(system)
                .singleRequest(HttpRequest.create().withUri(uri), Http.get(system).defaultClientHttpsContext(), settings, system.log(), materializer)
                .thenComposeAsync(responseHandler);
    }



}

public class RequestLevel {
    public static void main(String[] args) {
        System.out.println("Starting...");
        final ActorSystem system = ActorSystem.create();
        final Materializer materializer = ActorMaterializer.create(system);

        RequestLevelClient client = new RequestLevelClient(system, materializer);
        client.requestLevelFutureBased(success -> {
                    System.out.println("some result");
                    return success.entity()
                            .getDataBytes()
                            .runForeach(byteString -> System.out.println("Result: " + byteString.utf8String()),
                                    client.getMaterializer());
                }
        ).whenComplete((success, throwable) -> client.close());
    }
}
