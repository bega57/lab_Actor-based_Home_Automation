package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

/**
 * Actuator that controls whether the blinds are open or closed.
 *
 * Rules:
 *  - Sunny weather  → blinds close.
 *  - Non-sunny      → blinds open (unless a movie is playing).
 *  - Movie starts   → blinds close regardless of weather.
 *  - Movie stops    → blinds revert to weather-controlled state.
 *
 * Receptionist role:
 *  - Registers itself under SERVICE_KEY so that WeatherSensor and MediaStation
 *    can discover it without receiving a direct ActorRef via constructor.
 */
public class Blinds extends AbstractBehavior<Blinds.Command> {

    public static final ServiceKey<Command> SERVICE_KEY =
            ServiceKey.create(Command.class, "BlindsService");

    public interface Command {}

    public record GetStatus(ActorRef<StatusResponse> replyTo) implements Command {}
    public record StatusResponse(boolean closed)              {}

    /** Pushed by WeatherSensor when the weather condition changes. */
    public record WeatherUpdate(String condition) implements Command {}

    /** Pushed by MediaStation when a movie starts or stops. */
    public record MovieMode(boolean active) implements Command {}

    private boolean closed         = false;
    private boolean movieMode      = false;
    private String  currentWeather = "cloudy";

    public static Behavior<Command> create() {
        return Behaviors.setup(Blinds::new);
    }

    private Blinds(ActorContext<Command> context) {
        super(context);

        // Register in the Receptionist so WeatherSensor / MediaStation find us
        context.getSystem().receptionist().tell(
                Receptionist.register(SERVICE_KEY, context.getSelf())
        );

        getContext().getLog().info("Blinds started and registered");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(WeatherUpdate.class, this::onWeatherUpdate)
                .onMessage(MovieMode.class,     this::onMovieMode)
                .onMessage(GetStatus.class,     this::onGetStatus)
                .build();
    }

    private Behavior<Command> onWeatherUpdate(WeatherUpdate msg) {
        currentWeather = msg.condition();

        if (movieMode) {
            // Movie has priority – don't change blinds position
            return Behaviors.same();
        }

        if (msg.condition().equalsIgnoreCase("sunny")) {
            if (!closed) {
                closed = true;
                getContext().getLog().info("Blinds closing (sunny weather)");
            }
        } else {
            if (closed) {
                closed = false;
                getContext().getLog().info("Blinds opening (weather not sunny)");
            }
        }
        return Behaviors.same();
    }

    private Behavior<Command> onMovieMode(MovieMode msg) {
        movieMode = msg.active();

        if (movieMode) {
            if (!closed) {
                closed = true;
                getContext().getLog().info("Blinds closing (movie started)");
            }
        } else {
            getContext().getLog().info("Movie stopped – weather controls blinds again");
            // Revert to weather-driven state
            if (!currentWeather.equalsIgnoreCase("sunny") && closed) {
                closed = false;
                getContext().getLog().info("Blinds opening after movie (not sunny)");
            }
        }
        return Behaviors.same();
    }

    private Behavior<Command> onGetStatus(GetStatus msg) {
        msg.replyTo().tell(new StatusResponse(closed));
        return Behaviors.same();
    }
}
