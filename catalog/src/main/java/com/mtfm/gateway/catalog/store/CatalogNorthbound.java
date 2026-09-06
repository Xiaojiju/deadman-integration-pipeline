package com.mtfm.gateway.catalog.store;

import com.mtfm.gateway.catalog.dto.NorthboundView;
import com.mtfm.gateway.catalog.dto.NorthboundWriteRequest;
import com.mtfm.gateway.catalog.entity.NorthboundEntity;
import com.mtfm.gateway.catalog.mapper.NorthboundMapper;
import com.mtfm.gateway.spi.northbound.NorthboundLiveStatus;
import com.mtfm.gateway.spi.northbound.NorthboundSettings;
import com.mtfm.gateway.spi.port.NorthboundRuntime;
import com.mtfm.gateway.spi.secret.SecretCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 北向双通道落库与热切换。密码经 {@link SecretCodec} 密封；运行时 apply 失败不回滚已保存配置。
 */
@Service
public class CatalogNorthbound {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogNorthbound.class);
    static final String SECRET_MASK = "••••";

    private final NorthboundMapper mapper;
    private final SecretCodec secretCodec;
    private final ObjectProvider<NorthboundRuntime> runtime;

    public CatalogNorthbound(
            NorthboundMapper mapper,
            ObjectProvider<SecretCodec> secretCodec,
            ObjectProvider<NorthboundRuntime> runtime) {
        this.mapper = mapper;
        this.secretCodec = secretCodec == null
                ? SecretCodec.identity()
                : secretCodec.getIfAvailable(SecretCodec::identity);
        this.runtime = runtime;
    }

    /** 启动或保存后交给宿主；表不存在时当作未配置。 */
    public NorthboundSettings openedSettings() {
        return toSettings(loadQuietly(), secretCodec);
    }

    public NorthboundView view() {
        NorthboundRuntime binding = runtime == null ? null : runtime.getIfAvailable();
        NorthboundLiveStatus live = binding == null ? NorthboundLiveStatus.idle() : binding.status();
        return toView(loadQuietly(), live);
    }

    public NorthboundView update(NorthboundWriteRequest request) {
        NorthboundEntity existing = loadOrFail();
        NorthboundEntity next = merge(existing, request, secretCodec);
        persist(next);
        NorthboundRuntime binding = runtime == null ? null : runtime.getIfAvailable();
        if (binding != null) {
            binding.apply(toSettings(next, secretCodec));
        }
        return view();
    }

    static NorthboundEntity merge(
            NorthboundEntity existing, NorthboundWriteRequest request, SecretCodec codec) {
        if (request == null) {
            throw new IllegalArgumentException("北向配置不能为空");
        }
        SecretCodec secrets = codec == null ? SecretCodec.identity() : codec;
        NorthboundEntity entity = existing == null ? new NorthboundEntity() : existing;
        entity.setId(NorthboundEntity.DEFAULT_ID);
        if (request.mqttEnabled() != null) {
            entity.setMqttEnabled(request.mqttEnabled());
        } else if (entity.getMqttEnabled() == null) {
            entity.setMqttEnabled(false);
        }
        String transport = trimToNull(request.mqttTransport());
        if (transport != null) {
            validateTransport(transport);
            entity.setMqttTransport(transport);
        } else if (blank(entity.getMqttTransport())) {
            entity.setMqttTransport(NorthboundSettings.TRANSPORT_PAHO);
        }
        if (request.mqttUrl() != null) {
            entity.setMqttUrl(request.mqttUrl().trim());
        }
        if (request.mqttCommandTopic() != null) {
            entity.setMqttCommandTopic(request.mqttCommandTopic().trim());
        }
        if (request.mqttResponseTopic() != null) {
            entity.setMqttResponseTopic(request.mqttResponseTopic().trim());
        }
        if (request.mqttTelemetryTopic() != null) {
            entity.setMqttTelemetryTopic(request.mqttTelemetryTopic().trim());
        }
        if (request.mqttClientId() != null) {
            entity.setMqttClientId(request.mqttClientId().trim());
        }
        if (request.mqttUsername() != null) {
            entity.setMqttUsername(request.mqttUsername().trim());
        }
        if (!isMaskedOrBlank(request.mqttPassword())) {
            entity.setMqttPassword(secrets.seal(request.mqttPassword()));
        }
        if (request.httpEnabled() != null) {
            entity.setHttpEnabled(request.httpEnabled());
        } else if (entity.getHttpEnabled() == null) {
            entity.setHttpEnabled(false);
        }
        if (request.httpWebhookUrl() != null) {
            entity.setHttpWebhookUrl(request.httpWebhookUrl().trim());
        }
        if (request.httpTimeoutMs() != null) {
            if (request.httpTimeoutMs() <= 0) {
                throw new IllegalArgumentException("Webhook 超时必须大于 0");
            }
            entity.setHttpTimeoutMs(request.httpTimeoutMs());
        } else if (entity.getHttpTimeoutMs() == null) {
            entity.setHttpTimeoutMs(3000);
        }
        if (request.httpMaxAttempts() != null) {
            if (request.httpMaxAttempts() <= 0) {
                throw new IllegalArgumentException("Webhook 重试次数必须大于 0");
            }
            entity.setHttpMaxAttempts(request.httpMaxAttempts());
        } else if (entity.getHttpMaxAttempts() == null) {
            entity.setHttpMaxAttempts(2);
        }
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    static NorthboundSettings toSettings(NorthboundEntity entity, SecretCodec codec) {
        if (entity == null) {
            return NorthboundSettings.disabled();
        }
        SecretCodec secrets = codec == null ? SecretCodec.identity() : codec;
        String password = entity.getMqttPassword();
        String opened = password == null || password.isBlank() ? "" : nullToEmpty(secrets.open(password));
        return new NorthboundSettings(
                Boolean.TRUE.equals(entity.getMqttEnabled()),
                blankToDefault(entity.getMqttTransport(), NorthboundSettings.TRANSPORT_PAHO),
                nullToEmpty(entity.getMqttUrl()),
                blankToDefault(entity.getMqttCommandTopic(), NorthboundSettings.DEFAULT_COMMAND_TOPIC),
                blankToDefault(entity.getMqttResponseTopic(), NorthboundSettings.DEFAULT_RESPONSE_TOPIC),
                blankToDefault(entity.getMqttTelemetryTopic(), NorthboundSettings.DEFAULT_TELEMETRY_TOPIC),
                blankToDefault(entity.getMqttClientId(), NorthboundSettings.DEFAULT_CLIENT_ID),
                nullToEmpty(entity.getMqttUsername()),
                opened,
                Boolean.TRUE.equals(entity.getHttpEnabled()),
                nullToEmpty(entity.getHttpWebhookUrl()),
                entity.getHttpTimeoutMs() == null || entity.getHttpTimeoutMs() <= 0
                        ? 3000 : entity.getHttpTimeoutMs(),
                entity.getHttpMaxAttempts() == null || entity.getHttpMaxAttempts() <= 0
                        ? 2 : entity.getHttpMaxAttempts());
    }

    static NorthboundView toView(NorthboundEntity entity, NorthboundLiveStatus live) {
        NorthboundLiveStatus status = live == null ? NorthboundLiveStatus.idle() : live;
        if (entity == null) {
            NorthboundSettings defaults = NorthboundSettings.disabled();
            return new NorthboundView(
                    defaults.mqttEnabled(),
                    defaults.mqttTransport(),
                    defaults.mqttUrl(),
                    defaults.mqttCommandTopic(),
                    defaults.mqttResponseTopic(),
                    defaults.mqttTelemetryTopic(),
                    defaults.mqttClientId(),
                    defaults.mqttUsername(),
                    "",
                    false,
                    defaults.httpEnabled(),
                    defaults.httpWebhookUrl(),
                    defaults.httpTimeoutMs(),
                    defaults.httpMaxAttempts(),
                    status.mqttLive(),
                    status.mqttError(),
                    status.httpLive());
        }
        boolean passwordSet = entity.getMqttPassword() != null && !entity.getMqttPassword().isBlank();
        return new NorthboundView(
                Boolean.TRUE.equals(entity.getMqttEnabled()),
                blankToDefault(entity.getMqttTransport(), NorthboundSettings.TRANSPORT_PAHO),
                nullToEmpty(entity.getMqttUrl()),
                blankToDefault(entity.getMqttCommandTopic(), NorthboundSettings.DEFAULT_COMMAND_TOPIC),
                blankToDefault(entity.getMqttResponseTopic(), NorthboundSettings.DEFAULT_RESPONSE_TOPIC),
                blankToDefault(entity.getMqttTelemetryTopic(), NorthboundSettings.DEFAULT_TELEMETRY_TOPIC),
                blankToDefault(entity.getMqttClientId(), NorthboundSettings.DEFAULT_CLIENT_ID),
                nullToEmpty(entity.getMqttUsername()),
                passwordSet ? SECRET_MASK : "",
                passwordSet,
                Boolean.TRUE.equals(entity.getHttpEnabled()),
                nullToEmpty(entity.getHttpWebhookUrl()),
                entity.getHttpTimeoutMs() == null || entity.getHttpTimeoutMs() <= 0
                        ? 3000 : entity.getHttpTimeoutMs(),
                entity.getHttpMaxAttempts() == null || entity.getHttpMaxAttempts() <= 0
                        ? 2 : entity.getHttpMaxAttempts(),
                status.mqttLive(),
                status.mqttError(),
                status.httpLive());
    }

    private NorthboundEntity loadQuietly() {
        try {
            return mapper.selectById(NorthboundEntity.DEFAULT_ID);
        } catch (DataAccessException ex) {
            LOG.warn("北向配置表不可用，按未配置处理: {}", ex.getMessage());
            return null;
        }
    }

    private NorthboundEntity loadOrFail() {
        try {
            return mapper.selectById(NorthboundEntity.DEFAULT_ID);
        } catch (DataAccessException ex) {
            throw new IllegalStateException("北向配置表不可用，请先执行 V11__northbound.sql", ex);
        }
    }

    private void persist(NorthboundEntity entity) {
        try {
            NorthboundEntity current = mapper.selectById(NorthboundEntity.DEFAULT_ID);
            if (current == null) {
                mapper.insert(entity);
            } else {
                mapper.updateById(entity);
            }
        } catch (DataAccessException ex) {
            throw new IllegalStateException("北向配置保存失败，请先执行 V11__northbound.sql", ex);
        }
    }

    private static void validateTransport(String transport) {
        if (NorthboundSettings.TRANSPORT_PAHO.equalsIgnoreCase(transport)
                || NorthboundSettings.TRANSPORT_MEMORY.equalsIgnoreCase(transport)) {
            return;
        }
        throw new IllegalArgumentException("北向 MQTT 传输只能是 paho 或 memory");
    }

    private static boolean isMaskedOrBlank(String value) {
        return value == null || value.isBlank() || SECRET_MASK.equals(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToDefault(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }
}
