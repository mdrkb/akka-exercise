package de.hpi.ddm.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import de.hpi.ddm.structures.*;
import de.hpi.ddm.util.StringGenerator;

import java.util.ArrayList;
import java.util.List;

public class PasswordCracker extends AbstractLoggingActor {

    ////////////////////////
    // Actor Construction //
    ////////////////////////

    public static final String DEFAULT_NAME = "passwordCracker";

    public static Props props() {
        return Props.create(PasswordCracker.class, PasswordCracker::new);
    }

    ////////////////////
    // Actor Messages //
    ////////////////////


    public Receive createReceive() {
        return receiveBuilder()
                .match(PasswordHint.class, this::handle)
                .match(Availability.class, this::handle)
                .match(HintCracked.class, this::handle)
                .matchAny(object -> this.log().info("Received unknown message: \"{}\"", object.toString()))
                .build();
    }

    private void handle(PasswordHint message) {
        passwordCracker = this.sender();
        StringGenerator stringGenerator = new StringGenerator();
        List<Character> characterList = new ArrayList<>();
        for (char possibleCharacter : message.getChars().toCharArray()) {
            if (!message.getHints().contains(possibleCharacter)) {
                characterList.add(possibleCharacter);
            }
        }
        List<String> possiblePasswords = new ArrayList<>();
        int plen = message.getLength();
        stringGenerator.getStrings(characterList, plen, possiblePasswords);

        this.passwords = possiblePasswords;
        this.password = message.getPassword();
    }

    private void handle(Availability message) {
        String[] target = {password};
        Work work = new Work(target, passwords, this.self());
        message.getActorRef().tell(work, this.self());
    }

    private void handle(HintCracked message) {
        List<Character> result = message.getCharacterList();

        StringBuilder stringBuilder = new StringBuilder();
        for (Character character : result) {
            stringBuilder.append(character);
        }

        String string_result = stringBuilder.toString();
        passwordCracker.tell(new Result(string_result, 0), this.self());
    }

    /////////////////
    // Actor State //
    /////////////////

    private List<String> passwords;
    private String password;
    private ActorRef passwordCracker;
}
