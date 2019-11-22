package de.hpi.ddm.actors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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
        private ActorRef sender;
        private ActorRef receiver;

        public SourceRef<List<Byte>> getSourceRef() {
            return sourceRef;
        }

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
        Serialization serialization = SerializationExtension.get(getContext().getSystem());
        byte[] bytes = serialization.serialize(message).get();
        int serializerId = serialization.findSerializerFor(message).identifier();
        String manifest = Serializers.manifestFor(serialization.findSerializerFor(message), message);

        // Creating a serialized byte message with includes serializer Id and manifest
        SerializedByteMessage serializedByteMessage = new SerializedByteMessage(bytes, this.sender(), message.getReceiver(), serializerId, manifest);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(serializedByteMessage);
            objectOutputStream.flush();
            objectOutputStream.close();
            byteArrayOutputStream.close();
        } catch (IOException ex) {
            System.out.println("IOException is caught!");
        }

        byte[] byteArrayData = byteArrayOutputStream.toByteArray();

        // Akka Streaming
        Source<List<Byte>, NotUsed> source = Source.from(Arrays.asList(ArrayUtils.toObject(byteArrayData))).grouped(262144); // max size = 262144
        SourceRef<List<Byte>> sourceRef = source.runWith(StreamRefs.sourceRef(), getContext().getSystem());

        // Passing the source reference as a customized "SourceMessage"
        receiverProxy.tell(new SourceMessage(sourceRef, this.sender(), message.getReceiver()), this.self());

        // This will definitely fail in a distributed setting if the serialized message is large!
        // Solution options:
        // 1. Serialize the object and send its bytes batch-wise (make sure to use artery's side channel then).
        // 2. Serialize the object and send its bytes via Akka streaming.
        // 3. Send the object via Akka's http client-server component.
        // 4. Other ideas ...
        // receiverProxy.tell(new BytesMessage<>(message.getMessage(), this.sender(), message.getReceiver()), this.self());
    }

    private void handle(SourceMessage message) {
        // Receiving the customized "SourceMessage" and retrieving the source reference
        SourceRef<List<Byte>> sourceRef = message.getSourceRef();
        sourceRef.getSource().runWith(Sink.ignore(), getContext().getSystem()) // Send the way it is
                .whenComplete((result, exception) -> {
                    System.out.println("We have to gather the data now!");
                    System.out.println(result);
                });

        // We have to forward the final object to the final receiver
        message.getReceiver().tell("Deliver here the final assembled data", message.getSender());
    }

    private void handle(BytesMessage<?> message) {
        // Reassemble the message content, deserialize it and/or load the content from some local location before forwarding its content.
        message.getReceiver().tell(message.getBytes(), message.getSender());
    }
}
