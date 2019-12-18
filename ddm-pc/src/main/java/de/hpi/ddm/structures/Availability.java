package de.hpi.ddm.structures;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Availability implements Serializable {
    private static final long serialVersionUID = 12345678910111218L;
    private ActorRef actorRef;
}
