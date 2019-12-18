package de.hpi.ddm.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import de.hpi.ddm.structures.*;

import java.util.ArrayList;
import java.util.List;

public class Cracker extends AbstractLoggingActor {

    ////////////////////////
    // Actor Construction //
    ////////////////////////

    public static final String DEFAULT_NAME = "cracker";

    public static Props props(final ActorRef hintCracker, final ActorRef passwordCracker) {
        return Props.create(Cracker.class, () -> new Cracker(hintCracker, passwordCracker));
    }

    private Cracker(final ActorRef hintCracker, final ActorRef passwordCracker) {
        this.hintCracker = hintCracker;
        this.passwordCracker = passwordCracker;
    }

    ////////////////////
    // Actor Messages //
    ////////////////////


    public Receive createReceive() {
        return receiveBuilder()
                .match(Password.class, this::handle)
                .match(Availability.class, this::handle)
                .match(HintCracked.class, this::handle)
                .match(Result.class, this::handle)
                .matchAny(object -> this.log().info("Received unknown message: \"{}\"", object.toString()))
                .build();
    }

    private void handle(Password message) {
        hintsCracked = false;
        passwordCracked = false;
        this.password = message;
        this.master = this.sender();

        List<Character> characterList = new ArrayList<>();
        for (char ch : message.getChars().toCharArray()) {
            characterList.add(ch);
        }

        Hint hints = new Hint(message.getHints(), message.getStringList(), characterList, this.self());
        this.hintCracker.tell(hints, this.sender());
    }

    private void handle(Availability message) {
        if (!hintsCracked) {
            hintCracker.tell(message, this.self());
        } else {
            if (!passwordCracked)
                passwordCracker.tell(message, this.self());
            else {
                master.tell(message, this.self());
            }
        }
    }

    private void handle(HintCracked message) throws Exception {
        hintsCracked = true;
        PasswordHint hintedPassword = new PasswordHint(password.getChars(),
                password.getLength(), password.getPassword(), message.getCharacterList());
        this.passwordCracker.tell(hintedPassword, this.self());
    }

    private void handle(Result message) {
        if (!passwordCracked) {
            passwordCracked = true;
            message.setId(password.getId());
            master.tell(message, this.self());
        }
    }

    /////////////////
    // Actor State //
    /////////////////

    private final ActorRef hintCracker;
    private final ActorRef passwordCracker;
    private ActorRef master;
    private Password password;
    private boolean hintsCracked = false;
    private boolean passwordCracked = false;

    /////////////////////
    // Actor Lifecycle //
    /////////////////////

    @Override
    public void preStart() {
        Reaper.watchWithDefaultReaper(this);
    }

    ////////////////////
    // Actor Behavior //
    ////////////////////


}