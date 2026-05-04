package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class MediaStation extends AbstractBehavior<MediaStation.Command> {

    public interface Command {}

    public record PlayMovie(String name) implements Command {}
    public record StopMovie() implements Command {}

    private final ActorRef<Blinds.Command> blinds;
    private boolean isPlaying = false;

    public static Behavior<Command> create(ActorRef<Blinds.Command> blinds) {
        return Behaviors.setup(ctx -> new MediaStation(ctx, blinds));
    }

    private MediaStation(ActorContext<Command> context,
                         ActorRef<Blinds.Command> blinds) {
        super(context);
        this.blinds = blinds;
        getContext().getLog().info("MediaStation started");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PlayMovie.class, this::onPlayMovie)
                .onMessage(StopMovie.class, this::onStopMovie)
                .build();
    }

    private Behavior<Command> onPlayMovie(PlayMovie msg) {
        if (isPlaying) {
            getContext().getLog().info("A movie is already playing!");
            return this;
        }

        isPlaying = true;
        getContext().getLog().info("Playing movie: {}", msg.name);

        blinds.tell(new Blinds.MovieMode(true));

        return this;
    }

    private Behavior<Command> onStopMovie(StopMovie msg) {
        if (!isPlaying) {
            getContext().getLog().info("No movie is playing");
            return this;
        }

        isPlaying = false;
        getContext().getLog().info("Movie stopped");

        blinds.tell(new Blinds.MovieMode(false));

        return this;
    }
}