package de.hpi.ddm.actors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import de.hpi.ddm.structures.Result;
import de.hpi.ddm.structures.Password;
import de.hpi.ddm.structures.Availability;

public class Master extends AbstractLoggingActor {

    ////////////////////////
    // Actor Construction //
    ////////////////////////

    public static final String DEFAULT_NAME = "master";

    public static Props props(final ActorRef reader, final ActorRef collector, final ActorRef cracker) {
        return Props.create(Master.class, () -> new Master(reader, collector, cracker));
    }

    public Master(final ActorRef reader, final ActorRef collector, final ActorRef cracker) {
        this.reader = reader;
        this.collector = collector;
        this.workers = new ArrayList<>();
        this.cracker = cracker;
        this.hintSequences = new ArrayList<>();
    }

    ////////////////////
    // Actor Messages //
    ////////////////////

    @Data
    public static class StartMessage implements Serializable {
        private static final long serialVersionUID = -50374816448627600L;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchMessage implements Serializable {
        private static final long serialVersionUID = 8343040942748609598L;
        private List<String[]> lines;
    }

    @Data
    public static class RegistrationMessage implements Serializable {
        private static final long serialVersionUID = 3303081601659723997L;
    }

    /////////////////
    // Actor State //
    /////////////////

    private final ActorRef reader;
    private final ActorRef collector;
    private final List<ActorRef> workers;
    private final ActorRef cracker;
    private ArrayList<List<String>> hintSequences;
    private Stack<Password> passwordStack = new Stack<>();

    private long startTime;

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

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(StartMessage.class, this::handle)
                .match(BatchMessage.class, this::handle)
                .match(Terminated.class, this::handle)
                .match(RegistrationMessage.class, this::handle)
                .match(Availability.class, this::handle)
                .match(Result.class, this::handle)
                .matchAny(object -> this.log().info("Received unknown message: \"{}\"", object.toString()))
                .build();
    }

    protected void handle(StartMessage message) {
        this.startTime = System.currentTimeMillis();

        this.reader.tell(new Reader.ReadMessage(), this.self());
    }

    protected void handle(BatchMessage message) {

        ///////////////////////////////////////////////////////////////////////////////////////////////////////
        // The input file is read in batches for two reasons: /////////////////////////////////////////////////
        // 1. If we distribute the batches early, we might not need to hold the entire input data in memory. //
        // 2. If we process the batches early, we can achieve latency hiding. /////////////////////////////////
        // TODO: Implement the processing of the data for the concrete assignment. ////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        if (!message.getLines().isEmpty()) {

            String chars = null;
            for (String[] currentLine : message.getLines()) {
                int id = Integer.parseInt(currentLine[0]);
                String name = currentLine[1];
                chars = currentLine[2];
                int len = Integer.parseInt(currentLine[3]);
                String password = currentLine[4];
                String[] hints = Arrays.copyOfRange(currentLine, 5, currentLine.length);

                Password new_password = new Password(id, name, chars, len, password, hints, this.hintSequences);
                passwordStack.push(new_password);
            }

            if (this.hintSequences.isEmpty()) {
                for (int i = 0; i < chars.length(); i++) {
                    String temp_letters = chars;
                    String temp_string = temp_letters.replace(Character.toString(chars.charAt(i)), "");
                    List<String> list = new ArrayList<>();
                    heapPermutation(temp_string.toCharArray(), chars.length() - 1, 0, list);
                    this.hintSequences.add(list);
                }
            }

            if (!passwordStack.empty()) {
                this.cracker.tell(passwordStack.pop(), this.self());
            }

            this.collector.tell(new Collector.CollectMessage("Processed batch of size " + message.getLines().size()), this.self());
            this.reader.tell(new Reader.ReadMessage(), this.self());
        }
    }

    protected void terminate() {
        this.reader.tell(PoisonPill.getInstance(), ActorRef.noSender());
        this.collector.tell(PoisonPill.getInstance(), ActorRef.noSender());

        for (ActorRef worker : this.workers) {
            this.context().unwatch(worker);
            worker.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }

        this.self().tell(PoisonPill.getInstance(), ActorRef.noSender());

        long executionTime = System.currentTimeMillis() - this.startTime;
        this.log().info("Algorithm finished in {} ms", executionTime);
    }

    protected void handle(RegistrationMessage message) {
        this.context().watch(this.sender());
        this.workers.add(this.sender());
//		this.log().info("Registered {}", this.sender());
    }

    protected void handle(Terminated message) {
        this.context().unwatch(message.getActor());
        this.workers.remove(message.getActor());
//		this.log().info("Unregistered {}", message.getActor());
    }

    protected void handle(Availability message) {
        this.cracker.tell(message, this.self());
    }

    protected void handle(Result message) {
        if (!passwordStack.empty()) {
            cracker.tell(passwordStack.pop(), this.self());
        } else {
            this.collector.tell(new Collector.PrintMessage(), this.self());
            this.terminate();
        }
    }

    // Generating all permutations of an array using Heap's Algorithm
    // https://en.wikipedia.org/wiki/Heap's_algorithm
    // https://www.geeksforgeeks.org/heaps-algorithm-for-generating-permutations/
    private void heapPermutation(char[] a, int size, int n, List<String> l) {
        // If size is 1, store the obtained permutation
        if (size == 1)
            l.add(new String(a));

        for (int i = 0; i < size; i++) {
            heapPermutation(a, size - 1, n, l);

            // If size is odd, swap first and last element
            if (size % 2 == 1) {
                char temp = a[0];
                a[0] = a[size - 1];
                a[size - 1] = temp;
            }

            // If size is even, swap i-th and last element
            else {
                char temp = a[i];
                a[i] = a[size - 1];
                a[size - 1] = temp;
            }
        }
    }
}