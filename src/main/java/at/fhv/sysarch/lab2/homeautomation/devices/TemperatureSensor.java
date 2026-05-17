package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

/**
 * Measures the environmental temperature delivered by the EnvironmentActor
 * and forwards enriched temperature readings (with unit) to the AirCondition.
 *
 * Receptionist role:
 *  - Registers itself under SERVICE_KEY so the EnvironmentActor can discover it.
 *  - Subscribes to AirCondition.SERVICE_KEY to discover the AirCondition actor.
 *
 * Interaction pattern: Fire-and-Forget (sensor → AirCondition)
 */
public class TemperatureSensor extends AbstractBehavior<TemperatureSensor.TemperatureCommand> {

    public static final ServiceKey<TemperatureCommand> SERVICE_KEY =
            ServiceKey.create(TemperatureCommand.class, "TemperatureSensorService");

    public interface TemperatureCommand {}

    /** Raw temperature value pushed by the EnvironmentActor. */
    public record ReadTemperature(double value) implements TemperatureCommand {}

    /** Internal adapter to receive Receptionist listings for AirCondition. */
    private record AirConditionListing(Receptionist.Listing listing) implements TemperatureCommand {}

    private ActorRef<AirCondition.AirConditionCommand> airCondition = null;

    public static Behavior<TemperatureCommand> create() {
        return Behaviors.setup(TemperatureSensor::new);
    }

    private TemperatureSensor(ActorContext<TemperatureCommand> context) {
        super(context);

        // Register this actor in the Receptionist so EnvironmentActor can find it
        context.getSystem().receptionist().tell(
                Receptionist.register(SERVICE_KEY, context.getSelf())
        );

        // Subscribe to AirCondition registrations
        ActorRef<Receptionist.Listing> listingAdapter =
                context.messageAdapter(Receptionist.Listing.class, AirConditionListing::new);
        context.getSystem().receptionist().tell(
                Receptionist.subscribe(AirCondition.SERVICE_KEY, listingAdapter)
        );

        getContext().getLog().info("TemperatureSensor started and registered");
    }

    @Override
    public Receive<TemperatureCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(AirConditionListing.class, this::onAirConditionListing)
                .onMessage(ReadTemperature.class,     this::onReadTemperature)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<TemperatureCommand> onAirConditionListing(AirConditionListing msg) {
        msg.listing.getServiceInstances(AirCondition.SERVICE_KEY)
                .stream().findFirst().ifPresent(ref -> {
                    airCondition = ref;
                    getContext().getLog().info("AirCondition discovered via Receptionist");
                });
        return this;
    }

    private Behavior<TemperatureCommand> onReadTemperature(ReadTemperature r) {
        getContext().getLog().info("TemperatureSensor received {}", r.value());

        if (airCondition != null) {
            // Wrap the raw double in an enriched type that carries the unit
            airCondition.tell(new AirCondition.EnrichedTemperature(r.value(), "Celsius"));
        } else {
            getContext().getLog().warn("AirCondition not yet discovered – temperature reading dropped");
        }
        return this;
    }

    private TemperatureSensor onPostStop() {
        getContext().getLog().info("TemperatureSensor stopped");
        return this;
    }
}
