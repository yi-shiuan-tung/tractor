package io.github.ytung.tractor;

import org.atmosphere.config.service.DeliverTo;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Ready;
import org.atmosphere.cpr.AtmosphereResource;

import io.github.ytung.tractor.model.Game;

@ManagedService(path = "/tractor")
public class TractorRoom {

    @Ready(encoders = {JacksonEncoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public Game onReady(final AtmosphereResource r) {
        return new Game("Tractor");
    }
}
