package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

/**
 * Actuator that controls movie playback.
 *
 * Rules:
 *  - A new movie cannot be started while another is already playing.
 *  - Starting a movie closes the blinds (notifies Blinds via MovieMode).
 *  - Stopping a movie reopens the blinds (weather takes over again).
 *
 * Receptionist role:
 *  - Subscribes to Blinds.SERVICE_KEY to discover the Blinds actor without
 *    receiving a direct ActorRef via constructor.
 *
 * Movie title:
 *  - The title is part of the PlayMovie message, so any title can be passed
 *    from the HTTP layer.
 */
public class MediaStation extends AbstractBehavior<MediaStation.Command> {

    public interface Command {}

    public record GetStatus(ActorRef<StatusResponse> replyTo) implements Command {}
    public record StatusResponse(boolean playing, String currentTitle)  {}

    /** Start playing a movie with the given title. */
    public record PlayMovie(String title) implements Command {}

    /** Stop the currently playing movie. */
    public record StopMovie()             implements Command {}

    /** Internal adapter for Receptionist listings. */
    private record BlindsListing(Receptionist.Listing listing) implements Command {}

    private boolean isPlaying    = false;
    private String  currentTitle = "";

    private ActorRef<Blinds.Command> blinds = null;

    public static Behavior<Command> create() {
        return Behaviors.setup(MediaStation::new);
    }

    private MediaStation(ActorContext<Command> context) {
        super(context);

        // Subscribe to Blinds registrations
        ActorRef<Receptionist.Listing> adapter =
                context.messageAdapter(Receptionist.Listing.class, BlindsListing::new);
        context.getSystem().receptionist().tell(
                Receptionist.subscribe(Blinds.SERVICE_KEY, adapter)
        );

        getContext().getLog().info("MediaStation started");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(BlindsListing.class, this::onBlindsListing)
                .onMessage(PlayMovie.class,     this::onPlayMovie)
                .onMessage(StopMovie.class,     this::onStopMovie)
                .onMessage(GetStatus.class,     this::onGetStatus)
                .build();
    }

    private Behavior<Command> onBlindsListing(BlindsListing msg) {
        msg.listing.getServiceInstances(Blinds.SERVICE_KEY)
                .stream().findFirst().ifPresent(ref -> {
                    blinds = ref;
                    getContext().getLog().info("Blinds discovered via Receptionist");
                });
        return Behaviors.same();
    }

    private Behavior<Command> onPlayMovie(PlayMovie msg) {
        if (isPlaying) {
            getContext().getLog().info(
                    "Cannot play '{}' – '{}' is already running", msg.title(), currentTitle
            );
            return Behaviors.same();
        }

        isPlaying    = true;
        currentTitle = msg.title();
        getContext().getLog().info("Playing movie: {}", currentTitle);

        if (blinds != null) {
            blinds.tell(new Blinds.MovieMode(true));
        }
        return Behaviors.same();
    }

    private Behavior<Command> onStopMovie(StopMovie msg) {
        if (!isPlaying) {
            getContext().getLog().info("No movie is currently playing");
            return Behaviors.same();
        }

        getContext().getLog().info("Stopping movie: {}", currentTitle);
        isPlaying    = false;
        currentTitle = "";

        if (blinds != null) {
            blinds.tell(new Blinds.MovieMode(false));
        }
        return Behaviors.same();
    }

    private Behavior<Command> onGetStatus(GetStatus msg) {
        msg.replyTo().tell(new StatusResponse(isPlaying, currentTitle));
        return Behaviors.same();
    }
}
