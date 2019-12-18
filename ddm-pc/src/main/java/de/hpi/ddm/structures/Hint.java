package de.hpi.ddm.structures;

import akka.actor.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hint implements Serializable {
    private static final long serialVersionUID = 12345678910111215L;
    private String[] hints;
    private ArrayList<List<String>> stringList;
    private List<Character> characterList;
    private ActorRef actorRef;
}
