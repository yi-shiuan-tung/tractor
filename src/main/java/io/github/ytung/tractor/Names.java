
package io.github.ytung.tractor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.common.io.Files;

public class Names {

    private static final Random random = new Random();
    private static final List<String> adjectives = new ArrayList<>();
    private static final List<String> animals = new ArrayList<>();

    static {
        try {
            Files.readLines(new File("./src/main/resources/adjectives.txt"), StandardCharsets.UTF_8).forEach(adjectives::add);
            Files.readLines(new File("./src/main/resources/animals.txt"), StandardCharsets.UTF_8).forEach(animals::add);
        } catch (IOException e) {
            throw new IllegalStateException();
        }
    }

    public static String generateRandomName() {
        return String.format("%s %s",
            adjectives.get(random.nextInt(adjectives.size())),
            animals.get(random.nextInt(animals.size())));
    }

    private Names() {
    }
}
