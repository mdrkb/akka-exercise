package de.hpi.ddm.structures;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordHint implements Serializable {
    private static final long serialVersionUID = 12345678910111214L;
    private String chars;
    private int length;
    private String password;
    private List<Character> hints;
}
