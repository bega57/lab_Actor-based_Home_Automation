# Smart Home Automation System

## Overview

Actor-based smart home automation system built with Apache Pekko Typed.  
Simulates a living room and kitchen with sensors, actuators, and a smart fridge.

The system consists of **two independently deployable components**:

- **Home Automation System** – sensors, actuators, fridge, web dashboard
- **External Order Processing System** – gRPC server with H2 database persistence

---

## Prerequisites

- Java 17 or higher
- Gradle (included via `./gradlew`)
- IntelliJ IDEA (recommended) or any Java IDE

---

## Technologies

| Technology | Purpose |
|---|---|
| Apache Pekko Typed Actors | Actor-based concurrency |
| Pekko HTTP | REST API + Web UI |
| Pekko gRPC | Communication between the two systems |
| Eclipse Paho MQTT | External weather data source |
| H2 Database | Persistent order storage |
| Java | Implementation language |

---

## Running the Application

> **Start GreeterServer first.** If the Home Automation System starts
> before the gRPC server, the fridge cannot process orders.
> The rest of the system (sensors, AC, blinds, media) will still work normally.

### Step 1 – Start the Order Processing Server

In IntelliJ: Run **`GreeterServer.java`**  
*(located in `src/main/java/.../grpcdemo/GreeterServer.java`)*

Or via Gradle:
```bash
./gradlew run --args="grpc"
```

This starts the gRPC order processing server on **`localhost:8080`**.

---

### Step 2 – Start the Home Automation System

In IntelliJ: Run **`HomeAutomationSystem.java`**  
*(located in `src/main/java/.../HomeAutomationSystem.java`)*

Or via Gradle:
```bash
./gradlew run
```

This starts:
- The actor-based home automation system
- The REST API server
- The web dashboard

Available at: **`http://localhost:8084`**

> To stop the application, press **RETURN** in the terminal where it is running.

---

### MQTT (External Weather Source)

To receive live weather data from the FHV simulation server, connect to:

- **FHV VPN**, or
- **FHV eduroam Wi-Fi**

MQTT Broker: `10.0.40.161:1883`

Without VPN/eduroam the system starts normally and uses **internal simulation** instead.

---

## Web Dashboard

Open **`http://localhost:8084`** in your browser.

### Environment Controls
| Control | Description |
|---|---|
| Mode: Internal | Use actor-based temperature/weather simulation |
| Mode: External | Use live MQTT data from FHV broker |
| Mode: Off | Disable all automatic updates (manual values only) |
| Set Temperature | Manually set a fixed temperature value |
| Set Weather | Manually set weather: `sunny`, `cloudy`, `rain`, `storm`, `snow` |
| Simulation On/Off | Enable or disable the internal simulation ticks |

### Media Station
1. Type a **movie title** in the text field
2. Click **Play** to start — blinds close automatically
3. Click **Stop** to stop — blinds revert to weather-controlled state
4. Starting a second movie while one is playing is **not allowed**

### Smart Fridge
The fridge starts pre-filled with: `Milk (×2)`, `Red Bull (×2)`, `Pizza (×2)`, `Capri Sun (×2)`

| Action | How |
|---|---|
| Order product | Enter product name + amount → click Order |
| Consume product | Enter product name + amount → click Consume |
| Auto-reorder | Happens automatically when a product reaches 0 |
| View contents | Shown live in the dashboard |
| View order history | Shown in the Order History section |

> Orders are validated against weight limit (20 kg) and item limit (20 items).  
> Orders are forwarded via gRPC to the Order Processing Server and persisted in H2.

### Air Conditioning
Reacts automatically to temperature — no manual control needed.
- **ON** when temperature > 20 °C
- **OFF** when temperature ≤ 20 °C

---

## REST API Reference

| Endpoint | Description |
|---|---|
| `GET /status` | JSON status of all devices |
| `GET /fridge` | Current fridge contents as JSON |
| `GET /order/{name}/{amount}` | Order a product |
| `GET /consume/{name}/{amount}` | Consume a product |
| `GET /orderHistory` | Order history as JSON |
| `GET /playMovie/{title}` | Start a movie (URL-encoded title) |
| `GET /stopMovie` | Stop current movie |
| `GET /setTemperature/{value}` | Set temperature manually (−30 to 60) |
| `GET /setWeather/{condition}` | Set weather manually |
| `GET /mode/internal` | Switch to internal simulation |
| `GET /mode/external` | Switch to MQTT source |
| `GET /mode/off` | Disable all simulation |
| `GET /simulation/on` | Enable simulation ticks |
| `GET /simulation/off` | Disable simulation ticks |