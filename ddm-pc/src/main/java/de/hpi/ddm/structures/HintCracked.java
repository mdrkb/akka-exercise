package de.hpi.ddm.structures;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HintCracked implements Serializable {
    private static final long serialVersionUID = 12345678910111217L;
    private List<Character> characterList;
}
