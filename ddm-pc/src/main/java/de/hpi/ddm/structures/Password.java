package de.hpi.ddm.structures;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Password implements Serializable {
    private static final long serialVersionUID = -12345678910111216L;
    private int id;
    private String name;
    private String chars;
    private int length;
    private String password;
    private String[] hints;
    private ArrayList<List<String>> stringList;
}