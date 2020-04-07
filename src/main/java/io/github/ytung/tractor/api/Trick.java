package io.github.ytung.tractor.api;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class Trick {

    private final String startPlayerId;
    private final List<Play> plays = new ArrayList<>();
}
