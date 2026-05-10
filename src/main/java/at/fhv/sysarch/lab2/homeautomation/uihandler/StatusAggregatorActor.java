package at.fhv.sysarch.lab2.homeautomation.uihandler;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.devices.MediaStation;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.*;

public class StatusAggregatorActor extends AbstractBehavior<StatusAggregatorActor.Command> {

    public interface Command {}

    public record GetFullStatus(
            ActorRef<String> replyTo
    ) implements Command {}

    private record WrappedEnvironmentResponse(
            EnvironmentActor.StatusResponse response
    ) implements Command {}

    private record WrappedAirConditionResponse(
            AirCondition.StatusResponse response
    ) implements Command {}

    private record WrappedBlindsResponse(
            Blinds.StatusResponse response
    ) implements Command {}

    private record WrappedMediaResponse(
            MediaStation.StatusResponse response
    ) implements Command {}

    private final ActorRef<EnvironmentActor.Command> environment;
    private final ActorRef<AirCondition.AirConditionCommand> airCondition;
    private final ActorRef<Blinds.Command> blinds;
    private final ActorRef<MediaStation.Command> mediaStation;

    private ActorRef<String> pendingReply;

    private EnvironmentActor.StatusResponse environmentStatus;
    private AirCondition.StatusResponse airConditionStatus;
    private Blinds.StatusResponse blindsStatus;
    private MediaStation.StatusResponse mediaStatus;

    public static Behavior<Command> create(
            ActorRef<EnvironmentActor.Command> environment,
            ActorRef<AirCondition.AirConditionCommand> airCondition,
            ActorRef<Blinds.Command> blinds,
            ActorRef<MediaStation.Command> mediaStation,
            Scheduler scheduler
    ) {
        return Behaviors.setup(ctx ->
                new StatusAggregatorActor(
                        ctx,
                        environment,
                        airCondition,
                        blinds,
                        mediaStation
                )
        );
    }

    private StatusAggregatorActor(
            ActorContext<Command> context,
            ActorRef<EnvironmentActor.Command> environment,
            ActorRef<AirCondition.AirConditionCommand> airCondition,
            ActorRef<Blinds.Command> blinds,
            ActorRef<MediaStation.Command> mediaStation
    ) {
        super(context);

        this.environment = environment;
        this.airCondition = airCondition;
        this.blinds = blinds;
        this.mediaStation = mediaStation;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetFullStatus.class, this::onGetFullStatus)
                .onMessage(WrappedEnvironmentResponse.class, this::onEnvironmentResponse)
                .onMessage(WrappedAirConditionResponse.class, this::onAirConditionResponse)
                .onMessage(WrappedBlindsResponse.class, this::onBlindsResponse)
                .onMessage(WrappedMediaResponse.class, this::onMediaResponse)
                .build();
    }

    private Behavior<Command> onGetFullStatus(
            GetFullStatus msg
    ) {

        pendingReply = msg.replyTo;

        ActorRef<EnvironmentActor.StatusResponse> envAdapter =
                getContext().messageAdapter(
                        EnvironmentActor.StatusResponse.class,
                        WrappedEnvironmentResponse::new
                );

        ActorRef<AirCondition.StatusResponse> acAdapter =
                getContext().messageAdapter(
                        AirCondition.StatusResponse.class,
                        WrappedAirConditionResponse::new
                );

        ActorRef<Blinds.StatusResponse> blindsAdapter =
                getContext().messageAdapter(
                        Blinds.StatusResponse.class,
                        WrappedBlindsResponse::new
                );

        ActorRef<MediaStation.StatusResponse> mediaAdapter =
                getContext().messageAdapter(
                        MediaStation.StatusResponse.class,
                        WrappedMediaResponse::new
                );

        environment.tell(
                new EnvironmentActor.GetStatus(envAdapter)
        );

        airCondition.tell(
                new AirCondition.GetStatus(acAdapter)
        );

        blinds.tell(
                new Blinds.GetStatus(blindsAdapter)
        );

        mediaStation.tell(
                new MediaStation.GetStatus(mediaAdapter)
        );

        return Behaviors.same();
    }

    private Behavior<Command> onEnvironmentResponse(
            WrappedEnvironmentResponse msg
    ) {

        environmentStatus = msg.response;

        tryReply();

        return Behaviors.same();
    }

    private Behavior<Command> onAirConditionResponse(
            WrappedAirConditionResponse msg
    ) {

        airConditionStatus = msg.response;

        tryReply();

        return Behaviors.same();
    }

    private Behavior<Command> onBlindsResponse(
            WrappedBlindsResponse msg
    ) {

        blindsStatus = msg.response;

        tryReply();

        return Behaviors.same();
    }

    private Behavior<Command> onMediaResponse(
            WrappedMediaResponse msg
    ) {

        mediaStatus = msg.response;

        tryReply();

        return Behaviors.same();
    }

    private void tryReply() {

        if (
                environmentStatus == null ||
                        airConditionStatus == null ||
                        blindsStatus == null ||
                        mediaStatus == null
        ) {
            return;
        }

        String json =
                "{"
                        + "\"temperature\":" + environmentStatus.temperature() + ","
                        + "\"weather\":\"" + environmentStatus.weather() + "\","
                        + "\"airConditionOn\":" + airConditionStatus.isOn() + ","
                        + "\"blindsClosed\":" + blindsStatus.closed() + ","
                        + "\"moviePlaying\":" + mediaStatus.playing()
                        + "}";

        pendingReply.tell(json);

        environmentStatus = null;
        airConditionStatus = null;
        blindsStatus = null;
        mediaStatus = null;
    }
}