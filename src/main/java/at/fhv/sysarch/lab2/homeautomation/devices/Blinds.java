package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class Blinds extends AbstractBehavior<Blinds.Command> {

    public interface Command {}

    public record WeatherUpdate(String condition) implements Command {}

    public record MovieMode(boolean active) implements Command {}

    private boolean closed = false;
    private boolean movieMode = false;

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
                .build();
    }

    private Behavior<Command> onWeatherUpdate(WeatherUpdate msg) {

        if (movieMode) {
            return this; // Film hat Priorität
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

        return this;
    }

    private Behavior<Command> onMovieMode(MovieMode msg) {
        movieMode = msg.active;

        if (movieMode) {
            if (!closed) {
                closed = true;
                getContext().getLog().info("Blinds closing (movie playing)");
            }
        } else {
            getContext().getLog().info("Movie stopped, weather controls blinds again");
        }

        return this;
    }
}