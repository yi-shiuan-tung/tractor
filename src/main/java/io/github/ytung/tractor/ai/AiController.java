package io.github.ytung.tractor.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import io.github.ytung.tractor.Game;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.DeclareRequest;
import io.github.ytung.tractor.api.IncomingMessage.MakeKittyRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.InvalidAction;
import lombok.Data;

@Data
public class AiController {

    private final String myPlayerId;
    private final AiClient client;

    public void processMessage(Game game, OutgoingMessage message, Consumer<IncomingMessage> send) {
        if (message instanceof InvalidAction) {
            throw new IllegalStateException(((InvalidAction) message).getMessage());
        }

        if (game.getStatus() == GameStatus.DRAW) {
            Collection<Integer> declaredCardIds = client.declare(myPlayerId, game);
            if (declaredCardIds != null) {
                DeclareRequest request = new DeclareRequest();
                request.setCardIds(new ArrayList<>(declaredCardIds));
                send.accept(request);
            }
        }

        if (game.getStatus() == GameStatus.MAKE_KITTY
                && game.getCurrentPlayerIndex() != -1
                && game.getPlayerIds().get(game.getCurrentPlayerIndex()).equals(myPlayerId)
                && game.getKitty().isEmpty()) {
            MakeKittyRequest request = new MakeKittyRequest();
            request.setCardIds(new ArrayList<>(client.makeKitty(myPlayerId, game)));
            send.accept(request);
        }

        if (game.getStatus() == GameStatus.PLAY
                && game.getCurrentPlayerIndex() != -1
                && game.getPlayerIds().get(game.getCurrentPlayerIndex()).equals(myPlayerId)) {
            PlayRequest request = new PlayRequest();
            request.setCardIds(new ArrayList<>(client.play(myPlayerId, game)));
            request.setConfirmSpecialPlay(true);
            send.accept(request);
        }
    }
}
