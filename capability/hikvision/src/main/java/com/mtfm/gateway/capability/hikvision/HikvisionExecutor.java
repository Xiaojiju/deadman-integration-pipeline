package com.mtfm.gateway.capability.hikvision;

import com.mtfm.gateway.capability.hikvision.isapi.HikvisionHttpClient;
import com.mtfm.gateway.spi.capability.FunctionExecutor;
import com.mtfm.gateway.spi.model.Attributes;
import com.mtfm.gateway.spi.model.DeviceEndpointBinding;
import com.mtfm.gateway.spi.model.ExecutionResult;
import com.mtfm.gateway.spi.model.Failure;
import com.mtfm.gateway.spi.model.FunctionCommand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 海康 ISAPI 执行器：按 channelId 引用计数管理客户端连接，按 functionId 分发门禁指令。
 *
 * <p>
 * Channel（connection）共享 ISAPI 会话；Address（deviceSerialNo）区分子设备 devIndex。
 */
public final class HikvisionExecutor implements FunctionExecutor {

    private final Function<HikvisionChannelConfig, HikvisionClient> factory;
    private final ConcurrentHashMap<String, HikvisionClient> channels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HikvisionChannelConfig> channelConfigs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> refs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bound> devices = new ConcurrentHashMap<>();

    public HikvisionExecutor() {
        this(IsapiHikvisionClient::new);
    }

    public HikvisionExecutor(Function<HikvisionChannelConfig, HikvisionClient> factory) {
        this.factory = factory;
    }

    @Override
    public String capabilityType() {
        return HikvisionCapability.TYPE;
    }

    @Override
    public void bind(DeviceEndpointBinding binding) {
        String deviceSerialNo = binding.address().get("deviceSerialNo")
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("address.deviceSerialNo 必填"));
        Bound previous = devices.put(binding.deviceId(), new Bound(binding.channelId(), deviceSerialNo));
        if (previous != null) {
            releaseChannel(previous.channelId());
        }
        retainChannel(binding.channelId(), binding.connection());
    }

    @Override
    public boolean unbind(String deviceId) {
        Bound previous = devices.remove(deviceId);
        if (previous == null) {
            return false;
        }
        releaseChannel(previous.channelId());
        return true;
    }

    @Override
    public ExecutionResult execute(FunctionCommand command) {
        Bound bound = devices.get(command.deviceId());
        if (bound == null) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "未绑定端点: " + command.deviceId(), false));
        }
        HikvisionClient client = channels.get(bound.channelId());
        if (client == null) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), "通道未连接: " + bound.channelId(), false));
        }
        try {
            boolean ok = dispatch(client, bound, command);
            return ExecutionResult.success(command.requestId(), command.deviceId(), command.functionId(),
                    Map.of(
                            "deviceSerialNo", bound.deviceSerialNo(),
                            "channelId", bound.channelId(),
                            "success", ok));
        } catch (IllegalArgumentException ex) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), ex.getMessage(), false));
        } catch (HikvisionAccessException ex) {
            return ExecutionResult.failed(command,
                    Failure.executorError(capabilityType(), ex.getMessage(), ex.retryable()));
        }
    }

    /**
     * 检查通道是否打开
     * 
     * @param channelId 通道 ID
     * @return 是否打开
     */
    public boolean channelOpen(String channelId) {
        return channels.containsKey(channelId);
    }

    /**
     * 分发命令
     * 
     * @param client  客户端
     * @param bound   绑定
     * @param command 命令
     * @return 是否成功
     */
    private boolean dispatch(HikvisionClient client, Bound bound, FunctionCommand command) {
        Attributes args = command.arguments();
        return switch (command.functionId()) {
            case HikvisionCapability.FN_REMOTE_CONTROL_DOOR -> client.remoteControlDoor(
                    bound.deviceSerialNo(),
                    argOrDefault(args, "target", "65535"),
                    requireArg(args, "command"));
            case HikvisionCapability.FN_SET_UP_USER -> client.setUpUser(
                    bound.deviceSerialNo(),
                    requireArg(args, "employeeNo"),
                    requireArg(args, "name"),
                    arg(args, "beginTime"),
                    arg(args, "endTime"),
                    arg(args, "imgStr"));
            case HikvisionCapability.FN_MODIFY_USER -> client.modifyUser(
                    bound.deviceSerialNo(),
                    requireArg(args, "employeeNo"),
                    requireArg(args, "name"),
                    arg(args, "beginTime"),
                    arg(args, "endTime"),
                    arg(args, "imgStr"));
            case HikvisionCapability.FN_DELETE_USER -> client.deleteUser(
                    bound.deviceSerialNo(),
                    parseStringList(args, "employeeNoList"));
            case HikvisionCapability.FN_SET_UP_CARD -> client.setUpCard(
                    bound.deviceSerialNo(),
                    requireArg(args, "employeeNo"),
                    requireArg(args, "cardNo"),
                    arg(args, "cardType"));
            case HikvisionCapability.FN_MODIFY_CARD -> client.modifyCard(
                    bound.deviceSerialNo(),
                    requireArg(args, "employeeNo"),
                    requireArg(args, "cardNo"),
                    arg(args, "cardType"));
            case HikvisionCapability.FN_DELETE_CARD -> client.deleteCard(
                    bound.deviceSerialNo(),
                    parseStringList(args, "cardNoList"));
            default -> throw new IllegalArgumentException("不支持的功能: " + command.functionId());
        };
    }

    /**
     * 获取参数
     * 
     * @param args 参数
     * @param key  键
     * @return 参数
     */
    private static String arg(Attributes args, String key) {
        return args.get(key).map(String::valueOf).orElse(null);
    }

    /**
     * 获取参数或默认值
     * 
     * @param args         参数
     * @param key          键
     * @param defaultValue 默认值
     * @return 参数
     */
    private static String argOrDefault(Attributes args, String key, String defaultValue) {
        return args.get(key).map(String::valueOf).filter(value -> !value.isBlank()).orElse(defaultValue);
    }

    /**
     * 获取必填参数
     * 
     * @param args 参数
     * @param key  键
     * @return 参数
     */
    private static String requireArg(Attributes args, String key) {
        String value = arg(args, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("参数缺失: " + key);
        }
        return value;
    }

    /**
     * 解析字符串列表
     * 
     * @param args 参数
     * @param key  键
     * @return 字符串列表
     */
    private static List<String> parseStringList(Attributes args, String key) {
        Object raw = args.get(key).orElse(null);
        if (raw == null) {
            throw new IllegalArgumentException("参数缺失: " + key);
        }
        if (raw instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>();
            for (Object value : collection) {
                if (value != null) {
                    result.add(String.valueOf(value));
                }
            }
            if (result.isEmpty()) {
                throw new IllegalArgumentException("列表参数不能为空: " + key);
            }
            return result;
        }
        return parseStringList(String.valueOf(raw));
    }

    /**
     * 解析字符串列表
     * 
     * @param raw 原始字符串
     * @return 字符串列表
     */
    private static List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("列表参数不能为空");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) {
                throw new IllegalArgumentException("列表参数不能为空");
            }
            return List.of(inner.split(",")).stream()
                    .map(String::trim)
                    .map(HikvisionExecutor::stripQuotes)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        if (trimmed.contains(",")) {
            return List.of(trimmed.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of(trimmed);
    }

    /**
     * 去除引号
     * 
     * @param value 值
     * @return 去除引号后的值
     */
    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * 保留通道
     * 
     * @param channelId  通道 ID
     * @param connection 连接
     */
    private void retainChannel(String channelId, Attributes connection) {
        refs.computeIfAbsent(channelId, key -> new AtomicInteger()).incrementAndGet();
        channelConfigs.computeIfAbsent(channelId, id -> HikvisionChannelConfig.from(id, connection));
        channels.computeIfAbsent(channelId, id -> factory.apply(channelConfigs.get(id)));
    }

    /**
     * 释放通道
     *
     * @param channelId 通道 ID
     */
    private void releaseChannel(String channelId) {
        AtomicInteger count = refs.get(channelId);
        if (count == null) {
            return;
        }
        if (count.decrementAndGet() <= 0) {
            refs.remove(channelId);
            channelConfigs.remove(channelId);
            HikvisionClient client = channels.remove(channelId);
            if (client != null) {
                client.close();
            }
        }
    }

    /**
     * 关闭共享 HTTP 连接池。Spring {@code destroyMethod} 在进程退出时调用。
     */
    public void close() {
        HikvisionHttpClient.shutdown();
    }

    /**
     * 绑定
     * 
     * @param channelId      通道 ID
     * @param deviceSerialNo 设备序列号
     */
    private record Bound(String channelId, String deviceSerialNo) {
    }
}
