package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class WeatherSensor extends AbstractBehavior<WeatherSensor.Command> {

    public interface Command {}

    public record ReadWeather(String condition) implements Command {}

    private final ActorRef<Blinds.Command> blinds;

    public static Behavior<Command> create(ActorRef<Blinds.Command> blinds) {
        return Behaviors.setup(ctx -> new WeatherSensor(ctx, blinds));
    }

    private WeatherSensor(ActorContext<Command> context,
                          ActorRef<Blinds.Command> blinds) {
        super(context);
        this.blinds = blinds;
        getContext().getLog().info("WeatherSensor started");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ReadWeather.class, this::onReadWeather)
                .build();
    }

    private Behavior<Command> onReadWeather(ReadWeather msg) {
        getContext().getLog().info("WeatherSensor received {}", msg.condition);

        blinds.tell(new Blinds.WeatherUpdate(msg.condition));

        return this;
    }
}