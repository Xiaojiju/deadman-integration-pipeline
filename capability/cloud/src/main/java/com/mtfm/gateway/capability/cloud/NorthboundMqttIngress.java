package com.mtfm.gateway.capability.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 北向 MQTT 命令入站：解析 JSON 后交给 {@link NorthboundCommandPort}（catalog invoke）。
 */
public final class NorthboundMqttIngress implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NorthboundMqttIngress.class);

    private final NorthboundMqttSession session;
    private final NorthboundCommandPort port;
    private final String commandTopic;

    public NorthboundMqttIngress(
            NorthboundMqttSession session, NorthboundCommandPort port, String commandTopic) {
        this.session = session;
        this.port = port;
        this.commandTopic = (commandTopic == null || commandTopic.isBlank())
                ? NorthboundTopics.DEFAULT_COMMAND
                : commandTopic.trim();
    }

    public void start() {
        if (session == null || port == null) {
            return;
        }
        session.subscribe(commandTopic, this::onMessage);
        session.start();
    }

    void onMessage(String topic, String payload) {
        try {
            String deviceHint = NorthboundTopics.deviceIdFromTopic(commandTopic, topic);
            NorthboundCommand command = NorthboundJson.parseCommand(payload, deviceHint);
            port.submit(command);
        } catch (RuntimeException ex) {
            LOG.warn("北向 MQTT 命令丢弃 topic={}: {}", topic, ex.getMessage());
        }
    }

    @Override
    public void close() {
        // 与出站共用会话，由宿主关闭 NorthboundMqttSession
    }
}
