**Smart Home Automation System**

**Overview**

This project implements an actor-based smart home automation system using Apache Pekko.

The system simulates a living room and kitchen environment containing:

* Temperature Sensor
* Weather Sensor
* Air Conditioning
* Blinds
* Media Station
* Smart Fridge

Additionally, an external order-processing system was implemented using gRPC and H2 Database persistence.

The application also supports:

* internal environment simulation
* external MQTT weather source
* switching between simulation modes
* web-based dashboard UI

**Technologies**

* Java
* Apache Pekko Typed Actors
* Pekko HTTP
* Pekko gRPC
* Eclipse Paho MQTT
* H2 Database

**Running the Application**
**Important**

To receive external MQTT weather data, you must be connected to:

* FHV VPN
OR
* FHV eduroam WiFi

The MQTT broker used in this project is:

10.0.40.161:1883

**Start Order Processing Server**

Run:

GreeterServer.java

This starts the external gRPC order processing system on:

localhost:8080

**Start Home Automation System**

Run:

HomeAutomationSystem.java

This starts:

* the actor-based smart home system
* the REST server
* the web interface

**Open Web Interface**

Open:

http://localhost:8084

**Features**

**Environment**
* Internal simulation
* External MQTT source
* Manual temperature control
* Manual weather control
* Enable/disable simulation

**Media Station**
* Start movie
* Stop movie
* Blinds automatically close during movies

**Air Conditioning**
* Turns on above 20°C
* Turns off below 20°C

**Smart Fridge**
* Order products
* Consume products
* Automatic reorder when empty
* Weight capacity validation
* Product capacity validation
* Order history
* Persistent order storage in H2 database