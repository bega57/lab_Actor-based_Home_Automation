package at.fhv.sysarch.lab2.homeautomation.environment;

import org.eclipse.paho.client.mqttv3.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttEnvironmentClient {

    private final ActorRef<EnvironmentActor.Command> environment;

    public MqttEnvironmentClient(
            ActorRef<EnvironmentActor.Command> environment
    ) {
        this.environment = environment;
    }

    public void start() {

        try {

            MqttClient client = new MqttClient(
                    "tcp://10.0.40.161:1883",
                    MqttClient.generateClientId(),
                    new MemoryPersistence()
            );

            MqttConnectOptions options =
                    new MqttConnectOptions();

            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            client.connect(options);

            System.out.println("Connected to MQTT broker");

            client.subscribe("#", (topic, message) -> {

                String payload =
                        new String(message.getPayload());

                System.out.println(
                        "MQTT Topic: " + topic
                                + " | Message: " + payload
                );

                handleMessage(topic, payload);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(
            String topic,
            String payload
    ) {

        try {

            String lowerTopic =
                    topic.toLowerCase();

            if (lowerTopic.contains("temperature")) {

                String tempString =
                        payload
                                .split("\"temperature\":\"")[1]
                                .split("\"")[0];

                double value =
                        Double.parseDouble(tempString);

                /*
                 * External MQTT source sends temperatures
                 * in a different scale → normalize them
                 */
                value = value / 100.0;

                /*
                 * Ignore unrealistic temperatures
                 */
                if (value < -30 || value > 60) {

                    System.out.println(
                            "Invalid external temperature: "
                                    + value
                    );

                    return;
                }

                environment.tell(
                        new EnvironmentActor.ExternalTemperature(
                                value
                        )
                );
            }

            else if (
                    lowerTopic.contains("condition")
            ) {

                String condition =
                        payload
                                .split("\"condition\":\"")[1]
                                .split("\"")[0];

                environment.tell(
                        new EnvironmentActor.ExternalWeather(
                                condition
                        )
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "MQTT parse error: "
                            + e.getMessage()
            );
        }
    }
}