package at.fhv.sysarch.lab2;


import at.fhv.sysarch.lab2.homeautomation.HomeAutomationController;
import org.apache.pekko.actor.typed.ActorSystem;

public class HomeAutomationSystem {

    public static void main(String[] args) {
        ActorSystem<Void> home = ActorSystem.create(HomeAutomationController.create(), "HomeAutomation");
    }


}
