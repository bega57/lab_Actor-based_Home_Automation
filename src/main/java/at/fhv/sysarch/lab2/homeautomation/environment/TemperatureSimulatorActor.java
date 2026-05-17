package at.fhv.sysarch.lab2.homeautomation.environment;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.time.Duration;

/**
 * Separate actor responsible only for simulating the environmental temperature.
 * Periodically generates a random temperature change and reports it to the
 * EnvironmentActor (parent/coordinator) via a TemperatureTick message.
 *
 * Interaction pattern: Fire-and-Forget (tick → parent)
 */
public class TemperatureSimulatorActor extends AbstractBehavior<TemperatureSimulatorActor.Command> {

    public interface Command {}

    /** Internal timer message – sent by the scheduler to itself. */
    public static class Tick implements Command {}

    private final ActorRef<EnvironmentActor.Command> parent;
    private double temperature = 20.0;

    public static Behavior<Command> create(ActorRef<EnvironmentActor.Command> parent) {
        return Behaviors.setup(ctx ->
                Behaviors.withTimers(timers -> {
                    timers.startTimerAtFixedRate(new Tick(), Duration.ofSeconds(2));
                    return new TemperatureSimulatorActor(ctx, parent);
                })
        );
    }

    private TemperatureSimulatorActor(
            ActorContext<Command> context,
            ActorRef<EnvironmentActor.Command> parent
    ) {
        super(context);
        this.parent = parent;
        getContext().getLog().info("TemperatureSimulatorActor started");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .build();
    }

    private Behavior<Command> onTick(Tick msg) {
        // Random walk: ±1.5 °C per tick
        temperature += (Math.random() - 0.5) * 3.0;

        getContext().getLog().info(
                "TemperatureSimulator tick → {}", String.format("%.2f", temperature)
        );

        parent.tell(new EnvironmentActor.TemperatureTick(temperature));
        return Behaviors.same();
    }
}
