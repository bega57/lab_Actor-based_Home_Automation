package at.fhv.sysarch.lab2.homeautomation.uihandler;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.devices.MediaStation;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class StatusAggregatorActor extends AbstractBehavior<StatusAggregatorActor.Command> {

    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(3);

    public interface Command {}

    public record GetFullStatus(ActorRef<String> replyTo) implements Command {}

    private record AllCollected(
            ActorRef<String>                  replyTo,
            EnvironmentActor.StatusResponse   env,
            AirCondition.StatusResponse       ac,
            Blinds.StatusResponse             blinds,
            MediaStation.StatusResponse       media
    ) implements Command {}

    private record CollectFailed(
            ActorRef<String> replyTo,
            String           reason
    ) implements Command {}

    private final ActorRef<EnvironmentActor.Command>         environment;
    private final ActorRef<AirCondition.AirConditionCommand> airCondition;
    private final ActorRef<Blinds.Command>                   blinds;
    private final ActorRef<MediaStation.Command>             mediaStation;
    private final Scheduler                                  scheduler;

    public static Behavior<Command> create(
            ActorRef<EnvironmentActor.Command>         environment,
            ActorRef<AirCondition.AirConditionCommand> airCondition,
            ActorRef<Blinds.Command>                   blinds,
            ActorRef<MediaStation.Command>             mediaStation,
            Scheduler                                  scheduler
    ) {
        return Behaviors.setup(ctx ->
                new StatusAggregatorActor(ctx, environment, airCondition, blinds, mediaStation, scheduler)
        );
    }

    private StatusAggregatorActor(
            ActorContext<Command> context,
            ActorRef<EnvironmentActor.Command>         environment,
            ActorRef<AirCondition.AirConditionCommand> airCondition,
            ActorRef<Blinds.Command>                   blinds,
            ActorRef<MediaStation.Command>             mediaStation,
            Scheduler                                  scheduler
    ) {
        super(context);
        this.environment  = environment;
        this.airCondition = airCondition;
        this.blinds       = blinds;
        this.mediaStation = mediaStation;
        this.scheduler    = scheduler;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetFullStatus.class, this::onGetFullStatus)
                .onMessage(AllCollected.class,  this::onAllCollected)
                .onMessage(CollectFailed.class, this::onCollectFailed)
                .build();
    }

    private Behavior<Command> onGetFullStatus(GetFullStatus msg) {
        // Fire all 4 asks in parallel – each is a completely independent future
        CompletableFuture<EnvironmentActor.StatusResponse> envFuture =
                AskPattern.ask(environment,  EnvironmentActor.GetStatus::new,
                        ASK_TIMEOUT, scheduler).toCompletableFuture();

        CompletableFuture<AirCondition.StatusResponse> acFuture =
                AskPattern.ask(airCondition, AirCondition.GetStatus::new,
                        ASK_TIMEOUT, scheduler).toCompletableFuture();

        CompletableFuture<Blinds.StatusResponse> blindsFuture =
                AskPattern.ask(blinds,       Blinds.GetStatus::new,
                        ASK_TIMEOUT, scheduler).toCompletableFuture();

        CompletableFuture<MediaStation.StatusResponse> mediaFuture =
                AskPattern.ask(mediaStation, MediaStation.GetStatus::new,
                        ASK_TIMEOUT, scheduler).toCompletableFuture();

        // Combine: when ALL 4 succeed build AllCollected; on any failure → CollectFailed
        CompletableFuture<AllCollected> combined =
                CompletableFuture.allOf(envFuture, acFuture, blindsFuture, mediaFuture)
                        .thenApply(v -> new AllCollected(
                                msg.replyTo(),
                                envFuture.join(),
                                acFuture.join(),
                                blindsFuture.join(),
                                mediaFuture.join()
                        ));

        // pipeToSelf brings result back safely into the actor thread
        getContext().pipeToSelf(combined, (result, error) -> {
            if (error != null) return new CollectFailed(msg.replyTo(), error.getMessage());
            return result;
        });

        return Behaviors.same();
    }

    private Behavior<Command> onAllCollected(AllCollected msg) {
        String safeTitle = msg.media().currentTitle()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        String json = "{"
                + "\"temperature\":"    + msg.env().temperature()  + ","
                + "\"weather\":\""      + msg.env().weather()       + "\","
                + "\"airConditionOn\":" + msg.ac().isOn()           + ","
                + "\"blindsClosed\":"   + msg.blinds().closed()     + ","
                + "\"moviePlaying\":"   + msg.media().playing()     + ","
                + "\"currentTitle\":\"" + safeTitle                 + "\""
                + "}";

        msg.replyTo().tell(json);
        return Behaviors.same();
    }

    private Behavior<Command> onCollectFailed(CollectFailed msg) {
        getContext().getLog().warn("Status collection failed: {}", msg.reason());
        msg.replyTo().tell(
                "{\"temperature\":0,\"weather\":\"unknown\","
                        + "\"airConditionOn\":false,\"blindsClosed\":false,"
                        + "\"moviePlaying\":false,\"currentTitle\":\"\"}"
        );
        return Behaviors.same();
    }
}