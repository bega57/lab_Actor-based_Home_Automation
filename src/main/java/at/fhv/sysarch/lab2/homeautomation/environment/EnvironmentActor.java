package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.devices.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.WeatherSensor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentMode;

import java.time.Duration;

public class EnvironmentActor extends AbstractBehavior<EnvironmentActor.Command> {

    public interface Command {}
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements Command {}
    public record StatusResponse(double temperature, String weather) {}
    public static class Tick implements Command {}
    public record SetTemperature(double value) implements Command {}
    public record SetWeather(String condition) implements Command {}
    public record ToggleSimulation(boolean active) implements Command {}
    public record ChangeMode(EnvironmentMode mode) implements Command {}
    public record ExternalTemperature(double value) implements Command {}
    public record ExternalWeather(String condition) implements Command {}

    private double temperature = 20.0;
    private String weather = "cloudy";
    private boolean simulationActive = true;
    private final ActorRef<TemperatureSensor.TemperatureCommand> sensor;
    private final ActorRef<WeatherSensor.Command> weatherSensor;

    public static Behavior<Command> create(
            ActorRef<TemperatureSensor.TemperatureCommand> tempSensor,
            ActorRef<WeatherSensor.Command> weatherSensor
    ) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers -> {
                    timers.startTimerAtFixedRate(new Tick(), Duration.ofSeconds(2));
                    return new EnvironmentActor(context, tempSensor, weatherSensor);
                })
        );
    }

    private EnvironmentActor(ActorContext<Command> context,
                             ActorRef<TemperatureSensor.TemperatureCommand> sensor, ActorRef<WeatherSensor.Command> weatherSensor) {
        super(context);
        this.sensor = sensor;
        this.weatherSensor = weatherSensor;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .onMessage(SetTemperature.class, this::onSetTemperature)
                .onMessage(SetWeather.class, this::onSetWeather)
                .onMessage(ToggleSimulation.class, this::onToggleSimulation)
                .onMessage(ExternalTemperature.class, this::onExternalTemperature)
                .onMessage(ExternalWeather.class, this::onExternalWeather)
                .onMessage(ChangeMode.class, this::onChangeMode)
                .onMessage(GetStatus.class, this::onGetStatus)
                .build();
    }

    private Behavior<Command> onTick(Tick msg) {
        if (currentMode == EnvironmentMode.OFF) {
            return Behaviors.same();
        }
        if (currentMode != EnvironmentMode.INTERNAL) {
            return Behaviors.same();
        }
        if (!simulationActive) {
            return Behaviors.same();
        }
        temperature += (Math.random() - 0.5) * 3; // random change

        getContext().getLog().info("Temp now: {}", temperature);

        sensor.tell(new TemperatureSensor.ReadTemperature(temperature));

        weather =
                Math.random() > 0.5 ? "sunny" : "cloudy";


        weatherSensor.tell(new WeatherSensor.ReadWeather(weather));

        return Behaviors.same();
    }

    private Behavior<Command> onSetTemperature(
            SetTemperature msg
    ) {

        temperature = msg.value;


        sensor.tell(
                new TemperatureSensor.ReadTemperature(
                        temperature
                )
        );

        getContext().getLog().info(
                "Manual temperature set to {}",
                temperature
        );

        return Behaviors.same();
    }

    private Behavior<Command> onSetWeather(
            SetWeather msg
    ) {

        weather = msg.condition;

        weatherSensor.tell(
                new WeatherSensor.ReadWeather(
                        msg.condition
                )
        );

        getContext().getLog().info(
                "Manual weather set to {}",
                msg.condition
        );

        return Behaviors.same();
    }

    private Behavior<Command> onToggleSimulation(
            ToggleSimulation msg
    ) {

        simulationActive = msg.active;

        getContext().getLog().info(
                "Simulation active: {}",
                simulationActive
        );

        return Behaviors.same();
    }

    private Behavior<Command> onExternalTemperature(
            ExternalTemperature msg
    ) {
        if (currentMode == EnvironmentMode.OFF) {
            return Behaviors.same();
        }

        if (currentMode != EnvironmentMode.EXTERNAL) {
            return Behaviors.same();
        }

        temperature = msg.value;


        sensor.tell(
                new TemperatureSensor.ReadTemperature(
                        temperature
                )
        );

        getContext().getLog().info(
                "External MQTT temperature: {}",
                temperature
        );

        return Behaviors.same();
    }

    private Behavior<Command> onExternalWeather(
            ExternalWeather msg
    ) {
        if (currentMode == EnvironmentMode.OFF) {
            return Behaviors.same();
        }

        if (currentMode != EnvironmentMode.EXTERNAL) {
            return Behaviors.same();
        }

        weather = msg.condition;

        weatherSensor.tell(
                new WeatherSensor.ReadWeather(
                        msg.condition
                )
        );

        getContext().getLog().info(
                "External MQTT weather: {}",
                msg.condition
        );

        return Behaviors.same();
    }

    private EnvironmentMode currentMode = EnvironmentMode.INTERNAL;

    private Behavior<Command> onChangeMode(
            ChangeMode msg
    ) {

        currentMode = msg.mode;

        getContext().getLog().info(
                "Environment mode changed to {}",
                currentMode
        );

        return Behaviors.same();
    }

    private Behavior<Command> onGetStatus(
            GetStatus msg
    ) {

        msg.replyTo.tell(
                new StatusResponse(
                        temperature,
                        weather
                )
        );

        return Behaviors.same();
    }
}
