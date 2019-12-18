package de.hpi.ddm.structures;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Work implements Serializable {
    private static final long serialVersionUID = 12345678910111219L;
    private String[] targets;
    private List<String> stringList;
    private ActorRef actorRef;
}
