package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.devices.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.WeatherSensor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.time.Duration;

public class EnvironmentActor extends AbstractBehavior<EnvironmentActor.Command> {

    public interface Command {}
    public static class Tick implements Command {}

    private double temperature = 20.0;
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
                .build();
    }

    private Behavior<Command> onTick(Tick msg) {
        temperature += (Math.random() - 0.5) * 3; // random change

        getContext().getLog().info("Temp now: {}", temperature);

        sensor.tell(new TemperatureSensor.ReadTemperature(temperature));

        String weather = Math.random() > 0.5 ? "sunny" : "cloudy";

        weatherSensor.tell(new WeatherSensor.ReadWeather(weather));

        return this;
    }
}
