package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

/**
 * Measures the weather condition delivered by the EnvironmentActor and
 * forwards it to the Blinds actuator.
 *
 * Receptionist role:
 *  - Registers itself under SERVICE_KEY so the EnvironmentActor can discover it.
 *  - Subscribes to Blinds.SERVICE_KEY to discover the Blinds actor
 *    (no direct ActorRef is passed via constructor).
 *
 * Interaction pattern: Fire-and-Forget (sensor → Blinds)
 */
public class WeatherSensor extends AbstractBehavior<WeatherSensor.Command> {

    public static final ServiceKey<Command> SERVICE_KEY =
            ServiceKey.create(Command.class, "WeatherSensorService");

    public interface Command {}

    /** Raw weather condition pushed by the EnvironmentActor. */
    public record ReadWeather(String condition) implements Command {}

    /** Internal adapter to receive Receptionist listings for Blinds. */
    private record BlindsListing(Receptionist.Listing listing) implements Command {}

    private ActorRef<Blinds.Command> blinds = null;

    public static Behavior<Command> create() {
        return Behaviors.setup(WeatherSensor::new);
    }

    private WeatherSensor(ActorContext<Command> context) {
        super(context);

        // Register this actor so the EnvironmentActor can discover it
        context.getSystem().receptionist().tell(
                Receptionist.register(SERVICE_KEY, context.getSelf())
        );

        // Subscribe to Blinds registrations
        ActorRef<Receptionist.Listing> listingAdapter =
                context.messageAdapter(Receptionist.Listing.class, BlindsListing::new);
        context.getSystem().receptionist().tell(
                Receptionist.subscribe(Blinds.SERVICE_KEY, listingAdapter)
        );

        getContext().getLog().info("WeatherSensor started and registered");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(BlindsListing.class, this::onBlindsListing)
                .onMessage(ReadWeather.class,   this::onReadWeather)
                .build();
    }

    private Behavior<Command> onBlindsListing(BlindsListing msg) {
        msg.listing.getServiceInstances(Blinds.SERVICE_KEY)
                .stream().findFirst().ifPresent(ref -> {
                    blinds = ref;
                    getContext().getLog().info("Blinds discovered via Receptionist");
                });
        return this;
    }

    private Behavior<Command> onReadWeather(ReadWeather msg) {
        getContext().getLog().info("WeatherSensor received: {}", msg.condition());

        if (blinds != null) {
            blinds.tell(new Blinds.WeatherUpdate(msg.condition()));
        } else {
            getContext().getLog().warn("Blinds not yet discovered – weather update dropped");
        }
        return this;
    }
}
