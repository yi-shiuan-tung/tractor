package io.github.ytung.tractor;

import java.util.Random;

import org.atmosphere.config.service.DeliverTo;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Message;
import org.atmosphere.config.service.Ready;
import org.atmosphere.cpr.AtmosphereResource;

import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.Card.Suit;
import io.github.ytung.tractor.api.Card.Value;
import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.DrawRequest;
import io.github.ytung.tractor.api.IncomingMessage.StartGameRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.StartGame;
import io.github.ytung.tractor.api.OutgoingMessage.Welcome;

@ManagedService(path = "/tractor")
public class TractorRoom {

    @Ready(encoders = {JacksonEncoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public OutgoingMessage onReady(final AtmosphereResource r) {
        return new Welcome(r.uuid());
    }

    @Message(encoders = {JacksonEncoder.class}, decoders = {JacksonDecoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public OutgoingMessage onMessage(AtmosphereResource r, IncomingMessage message) {
        if (message instanceof StartGameRequest) {
            return new StartGame();
        } else if (message instanceof DrawRequest) {
            Random random = new Random();
            int id = random.nextInt();
            Value value = Value.values()[random.nextInt(Value.values().length)];
            Suit suit = Suit.values()[random.nextInt(Suit.values().length)];
            return new Draw(r.uuid(), new Card(id, value, suit));
        }
        throw new IllegalArgumentException("Invalid message.");
    }
}
