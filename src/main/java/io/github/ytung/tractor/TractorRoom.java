package io.github.ytung.tractor;

import org.atmosphere.config.service.DeliverTo;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Message;
import org.atmosphere.config.service.Ready;
import org.atmosphere.cpr.AtmosphereResource;

@ManagedService(path = "/tractor")
public class TractorRoom {

    @Ready(encoders = {JacksonEncoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public OutgoingMessage onReady(final AtmosphereResource r) {
        return new OutgoingMessage(String.format("Welcome to Tractor! You are %s", r.uuid()));
    }

    @Message(encoders = {JacksonEncoder.class}, decoders = {JacksonDecoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public OutgoingMessage onMessage(AtmosphereResource r, IncomingMessage message) {
        return new OutgoingMessage(String.format("%s sent this message: %s", r.uuid(), message.getMessage()));
    }
}
