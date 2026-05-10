package at.fhv.sysarch.lab2.homeautomation.uihandler;


import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import at.fhv.sysarch.lab2.homeautomation.devices.Fridge;
import at.fhv.sysarch.lab2.homeautomation.devices.MediaStation;
import org.apache.pekko.actor.typed.ActorRef;
import static org.apache.pekko.http.javadsl.server.PathMatchers.segment;
import static org.apache.pekko.http.javadsl.server.PathMatchers.integerSegment;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import java.time.Duration;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentMode;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
import static org.apache.pekko.http.javadsl.server.PathMatchers.doubleSegment;
import at.fhv.sysarch.lab2.homeautomation.uihandler.StatusAggregatorActor;
import at.fhv.sysarch.lab2.homeautomation.uihandler.OrderHistoryActor;


public class DemoHttpServer extends AllDirectives {

    private final ActorRef<Fridge.Command> fridge;
    private final ActorRef<MediaStation.Command> mediaStation;
    private final ActorRef<EnvironmentActor.Command> environment;
    private final Scheduler scheduler;
    private final ActorRef<StatusAggregatorActor.Command> statusAggregator;
    private final ActorRef<OrderHistoryActor.Command> orderHistory;

    public DemoHttpServer(
            ActorRef<Fridge.Command> fridge,
            ActorRef<MediaStation.Command> mediaStation,
            ActorRef<EnvironmentActor.Command> environment,
            ActorRef<StatusAggregatorActor.Command> statusAggregator,
            ActorRef<OrderHistoryActor.Command> orderHistory,
            Scheduler scheduler
    ) {
        this.fridge = fridge;
        this.mediaStation = mediaStation;
        this.environment = environment;
        this.statusAggregator = statusAggregator;
        this.orderHistory = orderHistory;
        this.scheduler = scheduler;
    }

    // TODO add your routes here, calling required actors. Also as the HTTP server lives outside of an Actor, you are allowed to pass your ActorRefs via Constructor of this class
    public Route createRoute() {
        return concat(
                pathSingleSlash(() ->
                        getFromResource("static/index.html")
                ),

                path("style.css", () ->
                        getFromResource("static/style.css")
                ),

                path("app.js", () ->
                        getFromResource("static/app.js")
                ),
                path("hello",
                        () -> get(() ->
                                complete("<h1>Home Automation running</h1>")
                        )
                ),
                path("playMovie",
                        () -> get(() -> {
                            mediaStation.tell(
                                    new MediaStation.PlayMovie("Interstellar")
                            );
                            return complete("Movie started");
                        })
                ),
                path("stopMovie",
                        () -> get(() -> {
                            mediaStation.tell(
                                    new MediaStation.StopMovie()
                            );
                            return complete("Movie stopped");
                        })
                ),
                path("fridge",
                        () -> get(() ->
                                onSuccess(
                                        AskPattern.ask(
                                                fridge,
                                                Fridge.GetContents::new,
                                                Duration.ofSeconds(3),
                                                scheduler
                                        ),
                                        response -> complete(
                                                HttpEntities.create(
                                                        ContentTypes.APPLICATION_JSON,
                                                        response.contents()
                                                )
                                        )
                                )
                        )
                ),
                pathPrefix("order", () ->
                        path(segment().slash(integerSegment()), (name, amount) ->
                                get(() -> {
                                    fridge.tell(
                                            new Fridge.OrderProduct(name, amount,1.0,2.5)
                                    );

                                    orderHistory.tell(
                                            new OrderHistoryActor.AddOrder(
                                                    name,
                                                    amount
                                            )
                                    );

                                    return complete(
                                            "Ordered " + amount + " x " + name
                                    );
                                })
                        )
                ),

                pathPrefix("consume", () ->
                        path(segment().slash(integerSegment()), (name, amount) ->
                                get(() -> {
                                    fridge.tell(
                                            new Fridge.ConsumeProduct(name, amount)
                                    );

                                    return complete(
                                            "Consumed " + amount + " x " + name
                                    );
                                })
                        )
                ),
                pathPrefix("mode", () ->
                        concat(

                                path("internal", () ->
                                        get(() -> {
                                            environment.tell(
                                                    new EnvironmentActor.ChangeMode(
                                                            EnvironmentMode.INTERNAL
                                                    )
                                            );

                                            return complete(
                                                    "Environment mode: INTERNAL"
                                            );
                                        })
                                ),

                                path("external", () ->
                                        get(() -> {
                                            environment.tell(
                                                    new EnvironmentActor.ChangeMode(
                                                            EnvironmentMode.EXTERNAL
                                                    )
                                            );

                                            return complete(
                                                    "Environment mode: EXTERNAL"
                                            );
                                        })
                                ),

                                path("off", () ->
                                        get(() -> {
                                            environment.tell(
                                                    new EnvironmentActor.ChangeMode(
                                                            EnvironmentMode.OFF
                                                    )
                                            );

                                            return complete(
                                                    "Environment mode: OFF"
                                            );
                                        })
                                )
                        )
                ),
                pathPrefix("simulation", () ->
                        concat(

                                path("on", () ->
                                        get(() -> {

                                            environment.tell(
                                                    new EnvironmentActor.ToggleSimulation(true)
                                            );

                                            return complete(
                                                    "Simulation enabled"
                                            );
                                        })
                                ),

                                path("off", () ->
                                        get(() -> {

                                            environment.tell(
                                                    new EnvironmentActor.ToggleSimulation(false)
                                            );

                                            return complete(
                                                    "Simulation disabled"
                                            );
                                        })
                                )
                        )
                ),
                pathPrefix("setTemperature", () ->
                        path(doubleSegment(), value ->
                                get(() -> {

                                    if (value < -30 || value > 60) {

                                        return complete(
                                                "Invalid temperature value"
                                        );
                                    }

                                    environment.tell(
                                            new EnvironmentActor.SetTemperature(
                                                    value
                                            )
                                    );

                                    return complete(
                                            "Temperature manually set to " + value
                                    );
                                })
                        )
                ),

                pathPrefix("setWeather", () ->
                        path(segment(), condition ->
                                get(() -> {
                                    String weather =
                                            condition.toLowerCase();

                                    if (
                                            !weather.equals("sunny") &&
                                                    !weather.equals("cloudy") &&
                                                    !weather.equals("rain") &&
                                                    !weather.equals("storm") &&
                                                    !weather.equals("snow")
                                    ) {

                                        return complete(
                                                "Invalid weather condition"
                                        );
                                    }

                                    environment.tell(
                                            new EnvironmentActor.SetWeather(
                                                    weather
                                            )
                                    );

                                    return complete(
                                            "Weather manually set to " + weather
                                    );
                                })
                        )
                ),
                path("orderHistory",
                        () -> get(() ->
                                onSuccess(
                                        AskPattern.ask(
                                                orderHistory,
                                                OrderHistoryActor.GetHistory::new,
                                                Duration.ofSeconds(3),
                                                scheduler
                                        ),
                                        json -> complete(
                                                HttpEntities.create(
                                                        ContentTypes.APPLICATION_JSON,
                                                        json
                                                )
                                        )
                                )
                        )
                ),

                path("clearHistory",
                        () -> get(() -> {

                            orderHistory.tell(
                                    new OrderHistoryActor.ClearHistory()
                            );

                            return complete(
                                    "History cleared"
                            );
                        })
                ),
                path("status",
                        () -> get(() ->
                                onSuccess(
                                        AskPattern.ask(
                                                statusAggregator,
                                                StatusAggregatorActor.GetFullStatus::new,
                                                Duration.ofSeconds(3),
                                                scheduler
                                        ),
                                        json -> complete(
                                                HttpEntities.create(
                                                        ContentTypes.APPLICATION_JSON,
                                                        json
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
