package de.hpi.ddm.util;

import java.util.List;

public class StringGenerator {
    public void getStrings(List<Character> characterList, int k, List<String> stringList) {
        int n = characterList.size();
        generateString(characterList, "", n, k, stringList);
    }

    private static void generateString(List<Character> characterList, String string, int n, int k, List<String> stringList) {
        if (k == 0) {
            stringList.add(string);
            return;
        }
        for (int i = 0; i < n; ++i) {
            String newPrefix = string + characterList.get(i);
            generateString(characterList, newPrefix, n, k - 1, stringList);
        }
    }
}


