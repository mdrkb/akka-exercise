package de.hpi.ddm.actors;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.stream.SourceRef;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

public class LargeMessageProxy extends AbstractLoggingActor {

    ////////////////////////
    // Actor Construction //
    ////////////////////////

    public static final String DEFAULT_NAME = "largeMessageProxy";

    public static Props props() {
        return Props.create(LargeMessageProxy.class);
    }

    ////////////////////
    // Actor Messages //
    ////////////////////

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LargeMessage<T> implements Serializable {
        private static final long serialVersionUID = 2940665245810221108L;
        private T message;
        private ActorRef receiver;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceMessage implements Serializable {
        private static final long serialVersionUID = 1234567743872319842L;
        private SourceRef<List<Byte>> sourceRef;
        private int length;
        private ActorRef sender;
        private ActorRef receiver;
    }

    /////////////////
    // Actor State //
    /////////////////

    /////////////////////
    // Actor Lifecycle //
    /////////////////////

    ////////////////////
    // Actor Behavior //
    ////////////////////

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(LargeMessage.class, this::handle)
                .match(SourceMessage.class, this::handle)
                .matchAny(object -> this.log().info("Received unknown message: \"{}\"", object.toString()))
                .build();
    }

    private void handle(LargeMessage<?> message) {
        ActorRef receiver = message.getReceiver();
        ActorSelection receiverProxy = this.context().actorSelection(receiver.path().child(DEFAULT_NAME));

        // Serializing the message
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            Kryo kryo = new Kryo();
            Output output = new Output(byteArrayOutputStream);
            kryo.writeClassAndObject(output, message.getMessage());
            output.close();
            byteArrayOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        byte[] byteArrayData = byteArrayOutputStream.toByteArray();

        // Akka Streaming
        Source<List<Byte>, NotUsed> source = Source.from(Arrays.asList(ArrayUtils.toObject(byteArrayData))).grouped(20000);
        SourceRef<List<Byte>> sourceRef = source.runWith(StreamRefs.sourceRef(), this.context().system());

        // Passing the source reference as a customized "SourceMessage"
        receiverProxy.tell(new SourceMessage(sourceRef, byteArrayData.length, this.sender(), message.getReceiver()), getSelf());
    }

    private void handle(SourceMessage message) {
        // Receiving the customized "SourceMessage" and retrieving the source reference
        SourceRef<List<Byte>> sourceRef = message.getSourceRef();
        byte[] bytes = new byte[message.getLength()];
        sourceRef.getSource().limit(message.getLength()).runWith(Sink.seq(), getContext().getSystem())
                .whenComplete((data, exception) -> {
                    int index = 0;
                    for (List<Byte> list : data) {
                        for (Byte abyte : list) {
                            bytes[index] = abyte;
                            index++;
                        }
                    }

                    // De-serializing the message object
                    Object messageObject = new Object();
                    try {
                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                        Kryo kryo = new Kryo();
                        Input input = new Input(byteArrayInputStream);
                        messageObject = kryo.readClassAndObject(input);
                        input.close();
                        byteArrayInputStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // Finally, we send the deserialize object to its destination
                    System.out.println("Message received!");
                    message.getReceiver().tell(messageObject, message.getSender());
                });
    }
}
