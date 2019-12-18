package de.hpi.ddm.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import de.hpi.ddm.structures.Hint;
import de.hpi.ddm.structures.HintCracked;
import de.hpi.ddm.structures.Availability;
import de.hpi.ddm.structures.Work;

import java.util.*;

public class HintCracker extends AbstractLoggingActor {

    ////////////////////////
    // Actor Construction //
    ////////////////////////

    public static final String DEFAULT_NAME = "hintCracker";

    public static Props props(final ActorRef PasswordCracker) {
        return Props.create(HintCracker.class, () -> new HintCracker(PasswordCracker));
    }

    public HintCracker(final ActorRef advancedPasswordSolver) {
        this.passwordCracker = advancedPasswordSolver;
    }

    ////////////////////
    // Actor Messages //
    ////////////////////


    public Receive createReceive() {
        return receiveBuilder()
                .match(Hint.class, this::handle)
                .match(Availability.class, this::handle)
                .match(HintCracked.class, this::handle)
                .matchAny(object -> this.log().info("Received unknown message: \"{}\"", object.toString()))
                .build();
    }

    protected void handle(Hint message) {
        this.hints = message.getHints();
        this.characterList = message.getCharacterList();
        this.passwordCracker = message.getActorRef();

        for (List<String> oneLetterList : message.getStringList()) {
            Work workPackage = new Work(this.hints, oneLetterList, this.self());
            this.workStack.push(workPackage);
        }
    }

    protected void handle(Availability message) {
        if (!this.workStack.empty()) {
            message.getActorRef().tell(this.workStack.pop(), this.sender());
        } else {
            this.passwordCracker.tell(message, this.sender());
        }
    }

    protected void handle(HintCracked message) {
        for (Character character : characterList) {
            if (!message.getCharacterList().contains(character)) {
                characterSet.add(character);
                if (characterSet.size() == hints.length) {
                    List<Character> allHints = new ArrayList<Character>(characterSet);
                    this.cracker.tell(new HintCracked(allHints), this.self());
                }
            }
        }
    }


    /////////////////
    // Actor State //
    /////////////////

    private String[] hints;
    private List<Character> characterList;
    private Set<Character> characterSet = new HashSet<Character>();
    private Stack<Work> workStack = new Stack<>();
    private ActorRef cracker;
    private ActorRef passwordCracker;

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
