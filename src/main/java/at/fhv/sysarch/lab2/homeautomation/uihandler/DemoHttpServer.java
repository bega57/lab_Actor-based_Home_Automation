package at.fhv.sysarch.lab2.homeautomation.uihandler;


import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;


public class DemoHttpServer extends AllDirectives {

    // TODO add your routes here, calling required actors. Also as the HTTP server lives outside of an Actor, you are allowed to pass your ActorRefs via Constructor of this class
    public Route createRoute() {
        return concat(path("hello", () -> get(() -> complete("<h1>Say hello to pekko-http</h1>"))));
    }
}
