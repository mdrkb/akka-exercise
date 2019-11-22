package de.hpi.ddm.actors;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import akka.serialization.Serializers;
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
    public static class BytesMessage<T> implements Serializable {
        private static final long serialVersionUID = 4057807743872319842L;
        private T bytes;
        private ActorRef sender;
        private ActorRef receiver;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerializedByteMessage<T> implements Serializable {
        private static final long serialVersionUID = 1234507743872319842L;
        private byte[] bytes;
        private ActorRef sender;
        private ActorRef receiver;
        private int serializerID;
        private String manifest;
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
                .match(BytesMessage.class, this::handle)
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
        Source<List<Byte>, NotUsed> source = Source.from(Arrays.asList(ArrayUtils.toObject(byteArrayData))).grouped(262144); // max size = 262144
        SourceRef<List<Byte>> sourceRef = source.runWith(StreamRefs.sourceRef(), getContext().getSystem());

        // Passing the source reference as a customized "SourceMessage"
        receiverProxy.tell(new SourceMessage(sourceRef, byteArrayData.length, this.sender(), message.getReceiver()), this.self());
    }

    private void handle(SourceMessage message) {
        // Receiving the customized "SourceMessage" and retrieving the source reference
        SourceRef<List<Byte>> sourceRef = message.getSourceRef();
        byte[] bytes = new byte[message.getLength()];
        sourceRef.getSource().runWith(Sink.seq(), getContext().getSystem()) // Send the way it is
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
                    message.receiver.tell(messageObject, message.sender);
                    System.out.println("Message received!");
                });
    }

    private void handle(BytesMessage<?> message) {
        // Reassemble the message content, deserialize it and/or load the content from some local location before forwarding its content.
        message.getReceiver().tell(message.getBytes(), message.getSender());
    }
}
