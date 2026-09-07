package com.mtfm.gateway.runtime.registry;

import com.mtfm.gateway.spi.capability.Publisher;
import com.mtfm.gateway.spi.exception.RegistryException;
import com.mtfm.gateway.spi.port.PublisherRegistry;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 北向 Publisher，同一 channelHint 只绑一个。
 */
public final class DefaultPublisherRegistry implements PublisherRegistry {

    private final ConcurrentHashMap<String, Publisher> publishers = new ConcurrentHashMap<>();

    @Override
    public boolean register(Publisher publisher) {
        if (publisher == null || publisher.channel() == null || publisher.channel().isBlank()) {
            throw new RegistryException("Publisher.channel 不能为空");
        }
        Publisher existing = publishers.putIfAbsent(publisher.channel(), publisher);
        if (existing != null && existing != publisher) {
            throw new RegistryException("同一 channelHint 只能绑一个 Publisher: " + publisher.channel());
        }
        return existing == null;
    }

    @Override
    public Optional<Publisher> findPublisher(String channelHint) {
        return Optional.ofNullable(publishers.get(channelHint));
    }
}
