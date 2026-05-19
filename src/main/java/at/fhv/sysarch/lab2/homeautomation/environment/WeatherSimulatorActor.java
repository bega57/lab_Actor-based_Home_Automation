package at.fhv.sysarch.lab2.homeautomation.environment;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.time.Duration;

/**
 * Separate actor responsible only for simulating the weather condition.
 * Periodically picks a random weather condition and reports it to the
 * EnvironmentActor (parent/coordinator) via a WeatherTick message.
 *
 * Interaction pattern: Fire-and-Forget (tick → parent)
 */
public class WeatherSimulatorActor extends AbstractBehavior<WeatherSimulatorActor.Command> {

    public interface Command {}

    /** Internal timer message – sent by the scheduler to itself. */
    public static class Tick implements Command {}

    private static final String[] CONDITIONS = {"sunny", "cloudy", "rain"};

    private final ActorRef<EnvironmentActor.Command> parent;
    private int currentIndex = 1; // start with "cloudy"

    public static Behavior<Command> create(ActorRef<EnvironmentActor.Command> parent) {
        return Behaviors.setup(ctx ->
                Behaviors.withTimers(timers -> {
                    timers.startTimerAtFixedRate(new Tick(), Duration.ofSeconds(5));
                    return new WeatherSimulatorActor(ctx, parent);
                })
        );
    }

    private WeatherSimulatorActor(
            ActorContext<Command> context,
            ActorRef<EnvironmentActor.Command> parent
    ) {
        super(context);
        this.parent = parent;
        getContext().getLog().info("WeatherSimulatorActor started");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .build();
    }

    private Behavior<Command> onTick(Tick msg) {
        currentIndex = (int) (Math.random() * CONDITIONS.length);
        String condition = CONDITIONS[currentIndex];

        getContext().getLog().info("WeatherSimulator tick → {}", condition);

        parent.tell(new EnvironmentActor.WeatherTick(condition));
        return Behaviors.same();
    }
}
