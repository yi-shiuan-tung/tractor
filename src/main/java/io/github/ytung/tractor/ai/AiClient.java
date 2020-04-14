package io.github.ytung.tractor.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.ytung.tractor.Game;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.PlayRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.PlayMessage;
import lombok.Data;

@Data
public class AiClient {

    private final String myId;

    public void processMessage(Game game, OutgoingMessage message, Consumer<IncomingMessage> send) {
        if (message instanceof PlayMessage) {
            if (game.getStatus() == GameStatus.PLAY
                    && game.getCurrentPlayerIndex() != -1
                    && game.getPlayerIds().get(game.getCurrentPlayerIndex()).equals(myId)) {
                PlayRequest request = new PlayRequest();
                request.setCardIds(play(game));
                request.setConfirmDoesItFly(true);
                send.accept(request);
            }
        }
    }

    private List<Integer> play(Game game) {
        List<Integer> myHand = game.getPlayerHands().get(myId);
        return new ArrayList<>(myHand.subList(0, 1));
    }
}
