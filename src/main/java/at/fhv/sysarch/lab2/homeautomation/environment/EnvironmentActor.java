package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.devices.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.WeatherSensor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

public class EnvironmentActor extends AbstractBehavior<EnvironmentActor.Command> {

    public interface Command {}

    // ---- from child simulators ----------------------------------------------
    public record TemperatureTick(double value)         implements Command {}
    public record WeatherTick(String condition)         implements Command {}

    // ---- from MQTT client ---------------------------------------------------
    public record ExternalTemperature(double value)     implements Command {}
    public record ExternalWeather(String condition)     implements Command {}

    // ---- manual overrides from HTTP/UI --------------------------------------
    public record SetTemperature(double value)          implements Command {}
    public record SetWeather(String condition)          implements Command {}

    // ---- mode control -------------------------------------------------------
    public record ChangeMode(EnvironmentMode mode)      implements Command {}
    public record ToggleSimulation(boolean active)      implements Command {}

    // ---- status query -------------------------------------------------------
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements Command {}
    public record StatusResponse(double temperature, String weather) {}

    // ---- internal: ONE adapter for ALL Receptionist.Listing messages --------
    private record ReceptionistListing(Receptionist.Listing listing) implements Command {}

    private double          temperature  = 20.0;
    private String          weather      = "cloudy";
    private EnvironmentMode mode         = EnvironmentMode.INTERNAL;
    private boolean         simActive    = true;

    private ActorRef<TemperatureSensor.TemperatureCommand> tempSensor    = null;
    private ActorRef<WeatherSensor.Command>               weatherSensor  = null;

    public static Behavior<Command> create() {
        return Behaviors.setup(EnvironmentActor::new);
    }

    private EnvironmentActor(ActorContext<Command> context) {
        super(context);

        // Spawn the two separate simulator child actors
        context.spawn(
                TemperatureSimulatorActor.create(context.getSelf()),
                "temperatureSimulator"
        );
        context.spawn(
                WeatherSimulatorActor.create(context.getSelf()),
                "weatherSimulator"
        );

        // ONE single adapter for ALL Receptionist.Listing messages.
        // Both subscriptions share this adapter; isForKey() in the handler
        // tells us which key each listing belongs to.
        ActorRef<Receptionist.Listing> listingAdapter =
                context.messageAdapter(Receptionist.Listing.class, ReceptionistListing::new);

        context.getSystem().receptionist().tell(
                Receptionist.subscribe(TemperatureSensor.SERVICE_KEY, listingAdapter)
        );
        context.getSystem().receptionist().tell(
                Receptionist.subscribe(WeatherSensor.SERVICE_KEY, listingAdapter)
        );

        getContext().getLog().info("EnvironmentActor started (mode=INTERNAL)");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ReceptionistListing.class,  this::onReceptionistListing)
                .onMessage(TemperatureTick.class,      this::onTemperatureTick)
                .onMessage(WeatherTick.class,          this::onWeatherTick)
                .onMessage(ExternalTemperature.class,  this::onExternalTemperature)
                .onMessage(ExternalWeather.class,      this::onExternalWeather)
                .onMessage(SetTemperature.class,       this::onSetTemperature)
                .onMessage(SetWeather.class,           this::onSetWeather)
                .onMessage(ChangeMode.class,           this::onChangeMode)
                .onMessage(ToggleSimulation.class,     this::onToggleSimulation)
                .onMessage(GetStatus.class,            this::onGetStatus)
                .build();
    }

    private Behavior<Command> onReceptionistListing(ReceptionistListing msg) {
        // isForKey() tells us which subscription sent this listing
        if (msg.listing().isForKey(TemperatureSensor.SERVICE_KEY)) {
            msg.listing().getServiceInstances(TemperatureSensor.SERVICE_KEY)
                    .stream().findFirst().ifPresent(ref -> {
                        tempSensor = ref;
                        getContext().getLog().info("TemperatureSensor discovered via Receptionist");
                    });
        }
        if (msg.listing().isForKey(WeatherSensor.SERVICE_KEY)) {
            msg.listing().getServiceInstances(WeatherSensor.SERVICE_KEY)
                    .stream().findFirst().ifPresent(ref -> {
                        weatherSensor = ref;
                        getContext().getLog().info("WeatherSensor discovered via Receptionist");
                    });
        }
        return Behaviors.same();
    }

    private Behavior<Command> onTemperatureTick(TemperatureTick msg) {
        if (mode != EnvironmentMode.INTERNAL || !simActive) return Behaviors.same();
        temperature = msg.value();
        forwardTemperature();
        return Behaviors.same();
    }

    private Behavior<Command> onWeatherTick(WeatherTick msg) {
        if (mode != EnvironmentMode.INTERNAL || !simActive) return Behaviors.same();
        weather = msg.condition();
        forwardWeather();
        return Behaviors.same();
    }

    private Behavior<Command> onExternalTemperature(ExternalTemperature msg) {
        if (mode != EnvironmentMode.EXTERNAL) return Behaviors.same();
        temperature = msg.value();
        getContext().getLog().info("MQTT temperature: {}", temperature);
        forwardTemperature();
        return Behaviors.same();
    }

    private Behavior<Command> onExternalWeather(ExternalWeather msg) {
        if (mode != EnvironmentMode.EXTERNAL) return Behaviors.same();
        weather = msg.condition();
        getContext().getLog().info("MQTT weather: {}", weather);
        forwardWeather();
        return Behaviors.same();
    }

    private Behavior<Command> onSetTemperature(SetTemperature msg) {
        temperature = msg.value();
        getContext().getLog().info("Manual temperature set to {}", temperature);
        forwardTemperature();
        return Behaviors.same();
    }

    private Behavior<Command> onSetWeather(SetWeather msg) {
        weather = msg.condition();
        getContext().getLog().info("Manual weather set to {}", weather);
        forwardWeather();
        return Behaviors.same();
    }

    private Behavior<Command> onChangeMode(ChangeMode msg) {
        mode = msg.mode();
        getContext().getLog().info("Environment mode changed to {}", mode);
        return Behaviors.same();
    }

    private Behavior<Command> onToggleSimulation(ToggleSimulation msg) {
        simActive = msg.active();
        getContext().getLog().info("Simulation active: {}", simActive);
        return Behaviors.same();
    }

    private Behavior<Command> onGetStatus(GetStatus msg) {
        msg.replyTo().tell(new StatusResponse(temperature, weather));
        return Behaviors.same();
    }

    private void forwardTemperature() {
        if (tempSensor != null) {
            tempSensor.tell(new TemperatureSensor.ReadTemperature(temperature));
        } else {
            getContext().getLog().warn("TemperatureSensor not yet discovered – skipping");
        }
    }

    private void forwardWeather() {
        if (weatherSensor != null) {
            weatherSensor.tell(new WeatherSensor.ReadWeather(weather));
        } else {
            getContext().getLog().warn("WeatherSensor not yet discovered – skipping");
        }
    }
}