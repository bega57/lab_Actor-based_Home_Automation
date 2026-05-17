package at.fhv.sysarch.lab2.homeautomation.environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pekko.actor.typed.ActorRef;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttEnvironmentClient {

    private static final String BROKER_URL   = "tcp://10.0.40.161:1883";
    private static final String SUBSCRIBE_ALL = "#";

    //Valid temperature range – readings outside this window are discarded
    private static final double TEMP_MIN = -30.0;
    private static final double TEMP_MAX =  60.0;

    private final ActorRef<EnvironmentActor.Command> environment;
    private final ObjectMapper                       mapper = new ObjectMapper();

    public MqttEnvironmentClient(ActorRef<EnvironmentActor.Command> environment) {
        this.environment = environment;
    }

    public void start() {
        try {
            MqttClient client = new MqttClient(
                    BROKER_URL,
                    MqttClient.generateClientId(),
                    new MemoryPersistence()
            );

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10); // seconds

            client.connect(options);
            System.out.println("[MQTT] Connected to " + BROKER_URL);

            client.subscribe(SUBSCRIBE_ALL, this::handleMessage);

        } catch (MqttException e) {
            // Not in FHV VPN / server unreachable – log and continue without MQTT
            System.err.println("[MQTT] Could not connect to broker: " + e.getMessage()
                    + " – external environment source unavailable.");
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        String payload = new String(message.getPayload()).trim();

        System.out.println("[MQTT] topic=" + topic + "  payload=" + payload);

        // Try to parse as JSON; fall back to raw-value handling
        try {
            if (payload.startsWith("{")) {
                handleJsonPayload(topic, payload);
            } else {
                handlePlainValuePayload(topic, payload);
            }
        } catch (Exception e) {
            System.err.println("[MQTT] Parse error on topic '" + topic
                    + "': " + e.getMessage() + "  payload=" + payload);
        }
    }

    private void handleJsonPayload(String topic, String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        String lowerTopic = topic.toLowerCase();

        if (lowerTopic.contains("temp")) {
            double value = extractTemperature(root);
            if (value == Double.NaN) {
                System.err.println("[MQTT] Could not extract temperature from: " + payload);
                return;
            }
            if (value < TEMP_MIN || value > TEMP_MAX) {
                System.err.println("[MQTT] Temperature out of range (" + value + ") – discarding");
                return;
            }
            environment.tell(new EnvironmentActor.ExternalTemperature(value));

        } else if (lowerTopic.contains("weather") || lowerTopic.contains("condition")) {
            String condition = extractWeather(root);
            if (condition == null) {
                System.err.println("[MQTT] Could not extract weather condition from: " + payload);
                return;
            }
            environment.tell(new EnvironmentActor.ExternalWeather(condition));

        } else {
            if (hasTemperatureField(root)) {
                double value = extractTemperature(root);
                if (value != Double.NaN && value >= TEMP_MIN && value <= TEMP_MAX) {
                    environment.tell(new EnvironmentActor.ExternalTemperature(value));
                }
            } else if (hasWeatherField(root)) {
                String condition = extractWeather(root);
                if (condition != null) {
                    environment.tell(new EnvironmentActor.ExternalWeather(condition));
                }
            } else {
                System.out.println("[MQTT] Unrecognised topic/payload – skipping: " + topic);
            }
        }
    }

    private void handlePlainValuePayload(String topic, String payload) {
        String lower = topic.toLowerCase();

        if (lower.contains("temp")) {
            try {
                double value = Double.parseDouble(payload);
                if (value >= TEMP_MIN && value <= TEMP_MAX) {
                    environment.tell(new EnvironmentActor.ExternalTemperature(value));
                }
            } catch (NumberFormatException e) {
                System.err.println("[MQTT] Expected numeric temperature, got: " + payload);
            }

        } else if (lower.contains("weather") || lower.contains("condition")) {
            String condition = payload.toLowerCase().trim();
            if (!condition.isBlank()) {
                environment.tell(new EnvironmentActor.ExternalWeather(condition));
            }
        }
    }

    private double extractTemperature(JsonNode root) {
        // The FHV server payload uses "temperature":"<value>" with numeric-as-string
        for (String key : new String[]{"temperature", "temp", "value", "t"}) {
            JsonNode node = root.get(key);
            if (node != null) {
                try {
                    double raw = node.isTextual()
                            ? Double.parseDouble(node.asText())
                            : node.asDouble();
                    // The FHV server sends values * 100 – normalise
                    return (raw > 100) ? raw / 100.0 : raw;
                } catch (NumberFormatException ignored) { /* try next */ }
            }
        }
        return Double.NaN;
    }

    private String extractWeather(JsonNode root) {
        for (String key : new String[]{"condition", "weather", "state", "status"}) {
            JsonNode node = root.get(key);
            if (node != null && node.isTextual()) {
                String val = node.asText().toLowerCase().trim();
                if (!val.isBlank()) return val;
            }
        }
        return null;
    }

    private boolean hasTemperatureField(JsonNode root) {
        for (String key : new String[]{"temperature", "temp", "value", "t"}) {
            if (root.has(key)) return true;
        }
        return false;
    }

    private boolean hasWeatherField(JsonNode root) {
        for (String key : new String[]{"condition", "weather", "state", "status"}) {
            if (root.has(key)) return true;
        }
        return false;
    }
}