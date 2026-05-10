package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;


public class Blinds extends AbstractBehavior<Blinds.Command> {

    public interface Command {}
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements Command {}
    public record StatusResponse(boolean closed) {}

    public record WeatherUpdate(String condition) implements Command {}

    public record MovieMode(boolean active) implements Command {}

    private boolean closed = false;
    private boolean movieMode = false;
    private String currentWeather = "cloudy";

    public static Behavior<Command> create() {
        return Behaviors.setup(Blinds::new);
    }

    private Blinds(ActorContext<Command> context) {
        super(context);
        getContext().getLog().info("Blinds started");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(WeatherUpdate.class, this::onWeatherUpdate)
                .onMessage(MovieMode.class, this::onMovieMode)
                .onMessage(GetStatus.class, this::onGetStatus)
                .build();
    }

    private Behavior<Command> onWeatherUpdate(WeatherUpdate msg) {
        currentWeather = msg.condition;

        if (movieMode) {
            return Behaviors.same(); // Film hat Priorität
        }

        if (msg.condition.equalsIgnoreCase("sunny")) {
            if (!closed) {
                closed = true;

                getContext().getLog().info("Blinds closing (sunny)");
            }
        } else {
            if (closed) {
                closed = false;

                getContext().getLog().info("Blinds opening (not sunny)");
            }
        }

        return Behaviors.same();
    }

    private Behavior<Command> onMovieMode(MovieMode msg) {
        movieMode = msg.active;

        if (movieMode) {
            if (!closed) {
                closed = true;

                getContext().getLog().info("Blinds closing (movie playing)");
            }
        } else {

            getContext().getLog().info(
                    "Movie stopped, weather controls blinds again"
            );

            if (!currentWeather.equalsIgnoreCase("sunny")) {

                closed = false;

                getContext().getLog().info(
                        "Blinds opening after movie"
                );
            }
        }

        return Behaviors.same();
    }

    private Behavior<Command> onGetStatus(
            GetStatus msg
    ) {

        msg.replyTo.tell(
                new StatusResponse(closed)
        );

        return Behaviors.same();
    }
}