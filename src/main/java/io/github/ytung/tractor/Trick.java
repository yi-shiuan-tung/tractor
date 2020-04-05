package io.github.ytung.tractor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.github.ytung.tractor.Cards.Grouping;
import io.github.ytung.tractor.api.Card;
import lombok.Data;

@Data
public class Trick {

    private final String startPlayerId;
    private final List<Play> plays = new ArrayList<>();

    public String winningPlayerId(Card trump) {
        String winningPlayerId = startPlayerId;
        List<Component> bestProfile = plays.get(0).getProfile(trump);
        Grouping bestGrouping = plays.get(0).getGrouping(trump);
        for (int i = 1; i < plays.size(); i++) {
            Play play = plays.get(i);
            List<Component> profile = play.getProfile(trump);
            Grouping grouping = play.getGrouping(trump);
            if (getShapes(profile).equals(getShapes(bestProfile))) {
                if (grouping == Grouping.TRUMP && bestGrouping != Grouping.TRUMP
                        || grouping == bestGrouping && profile.get(0).getMaxRank() > bestProfile.get(0).getMaxRank()) {
                    winningPlayerId = play.getPlayerId();
                    bestProfile = profile;
                    bestGrouping = grouping;
                }
            }
        }
        return winningPlayerId;
    }

    private static Multiset<Shape> getShapes(List<Component> profile) {
        return HashMultiset.create(profile.stream().map(Component::getShape).collect(Collectors.toList()));
    }
}
