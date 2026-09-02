package com.mtfm.gateway.spi.payload;

import com.mtfm.gateway.spi.property.WriteFieldOption;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按字段顺序把逻辑 Map 打成定长帧，或从帧切片还原字段。
 *
 * <p>HEX 输出为空格分隔的大写十六进制，例如 {@code 00 00 00 00 00 01}。
 * BINARY 用 ISO-8859-1 把字节塞进 {@code _value}，以便现有 MQTT 字符串发布路径原样下发。
 */
public final class FramePacker {

    public static final String VALUE_KEY = "_value";

    private FramePacker() {
    }

    public static Map<String, Object> pack(
            List<WriteFieldOption> fields,
            Map<String, Object> values,
            PayloadEncoding encoding) {
        if (encoding == null || !encoding.isFramed()) {
            return values == null ? Map.of() : values;
        }
        if (values != null && values.size() == 1 && values.containsKey(VALUE_KEY)) {
            return Map.of(VALUE_KEY, encodeScalar(values.get(VALUE_KEY), encoding));
        }
        byte[] frame = encodeFields(fields, values);
        return Map.of(VALUE_KEY, encodeFrame(frame, encoding));
    }

    public static Map<String, Object> unpack(
            String text,
            List<WriteFieldOption> fields,
            PayloadEncoding encoding) {
        if (encoding == null || !encoding.isFramed()) {
            return Map.of();
        }
        byte[] frame = decodeFrame(text, encoding);
        if (frame.length == 0) {
            return Map.of();
        }
        if (fields == null || fields.isEmpty()) {
            return Map.of(VALUE_KEY, encodeFrame(frame, encoding));
        }
        Map<String, Object> points = new LinkedHashMap<>();
        int offset = 0;
        for (WriteFieldOption field : fields) {
            if (field == null || field.field() == null || field.field().isBlank()) {
                continue;
            }
            int width = widthOf(field);
            if (offset + width > frame.length) {
                break;
            }
            byte[] slice = new byte[width];
            System.arraycopy(frame, offset, slice, 0, width);
            offset += width;
            points.put(field.field(), decodeValue(field, slice));
        }
        return points.isEmpty() ? Map.of(VALUE_KEY, encodeFrame(frame, encoding)) : Map.copyOf(points);
    }

    static String formatHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(bytes.length * 3 - 1);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        return out.toString();
    }

    static byte[] parseHex(String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ':' || c == '-') {
                continue;
            }
            hex.append(c);
        }
        if (hex.length() >= 2 && hex.charAt(0) == '0' && (hex.charAt(1) == 'x' || hex.charAt(1) == 'X')) {
            hex.delete(0, 2);
        }
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("hex 长度必须为偶数: " + text);
        }
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("非法 hex: " + text);
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private static byte[] encodeFields(List<WriteFieldOption> fields, Map<String, Object> values) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("HEX/BINARY 编码需要配置字段及 byteLength");
        }
        int total = 0;
        for (WriteFieldOption field : fields) {
            total += widthOf(field);
        }
        byte[] frame = new byte[total];
        int offset = 0;
        for (WriteFieldOption field : fields) {
            byte[] part = encodeValue(field, lookup(values, field.field()));
            System.arraycopy(part, 0, frame, offset, part.length);
            offset += part.length;
        }
        return frame;
    }

    private static Object lookup(Map<String, Object> values, String path) {
        if (values == null || path == null || path.isBlank()) {
            return null;
        }
        Object direct = values.get(path);
        if (direct != null) {
            return direct;
        }
        return FieldTreePaths.getNested(values, path);
    }

    private static byte[] encodeValue(WriteFieldOption field, Object value) {
        int width = widthOf(field);
        boolean little = isLittle(field);
        if (value == null) {
            return new byte[width];
        }
        if (value instanceof byte[] raw) {
            return fit(raw, width, little);
        }
        if (value instanceof Boolean flag) {
            return encodeUnsigned(flag ? 1L : 0L, width, little);
        }
        if (value instanceof Number number) {
            return encodeUnsigned(number.longValue(), width, little);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return new byte[width];
        }
        if (looksLikeHex(text)) {
            return fit(parseHex(text), width, little);
        }
        if (isNumericType(field) || looksLikeNumber(text)) {
            return encodeUnsigned(Long.parseLong(text), width, little);
        }
        return fit(text.getBytes(StandardCharsets.UTF_8), width, little);
    }

    private static Object decodeValue(WriteFieldOption field, byte[] slice) {
        String type = field.transformDataType();
        if (type == null || type.isBlank()) {
            type = field.accessDataType();
        }
        if (type != null && "boolean".equalsIgnoreCase(type)) {
            return slice[slice.length - 1] != 0;
        }
        if (type != null && ("string".equalsIgnoreCase(type) || "select".equalsIgnoreCase(type)
                || "password".equalsIgnoreCase(type))) {
            return formatHex(slice);
        }
        long value = decodeUnsigned(slice, isLittle(field));
        if (slice.length <= 4 && value <= Integer.MAX_VALUE) {
            return (int) value;
        }
        return value;
    }

    private static byte[] encodeUnsigned(long value, int width, boolean little) {
        byte[] out = new byte[width];
        for (int i = 0; i < width; i++) {
            int shift = 8 * (little ? i : width - 1 - i);
            out[i] = (byte) ((value >>> shift) & 0xFF);
        }
        return out;
    }

    private static long decodeUnsigned(byte[] slice, boolean little) {
        long value = 0;
        if (little) {
            for (int i = slice.length - 1; i >= 0; i--) {
                value = (value << 8) | (slice[i] & 0xFF);
            }
        } else {
            for (byte b : slice) {
                value = (value << 8) | (b & 0xFF);
            }
        }
        return value;
    }

    private static byte[] fit(byte[] raw, int width, boolean little) {
        if (raw.length == width) {
            return raw;
        }
        byte[] out = new byte[width];
        if (raw.length > width) {
            if (little) {
                System.arraycopy(raw, 0, out, 0, width);
            } else {
                System.arraycopy(raw, raw.length - width, out, 0, width);
            }
            return out;
        }
        if (little) {
            System.arraycopy(raw, 0, out, 0, raw.length);
        } else {
            System.arraycopy(raw, 0, out, width - raw.length, raw.length);
        }
        return out;
    }

    private static int widthOf(WriteFieldOption field) {
        Integer length = field == null ? null : field.byteLength();
        if (length == null || length <= 0) {
            return 1;
        }
        return length;
    }

    private static boolean isLittle(WriteFieldOption field) {
        return field != null && "little".equalsIgnoreCase(field.byteOrder());
    }

    private static boolean isNumericType(WriteFieldOption field) {
        String type = field.transformDataType();
        if (type == null || type.isBlank()) {
            type = field.accessDataType();
        }
        return type != null && ("int".equalsIgnoreCase(type) || "boolean".equalsIgnoreCase(type));
    }

    private static boolean looksLikeNumber(String text) {
        if (text.isEmpty()) {
            return false;
        }
        int i = text.charAt(0) == '-' ? 1 : 0;
        if (i >= text.length()) {
            return false;
        }
        for (; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeHex(String text) {
        int digits = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ':' || c == '-') {
                continue;
            }
            if (c == 'x' || c == 'X') {
                continue;
            }
            if (Character.digit(c, 16) < 0) {
                return false;
            }
            digits++;
        }
        return digits > 0 && (digits & 1) == 0;
    }

    private static String encodeScalar(Object value, PayloadEncoding encoding) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] raw) {
            return encodeFrame(raw, encoding);
        }
        String text = String.valueOf(value);
        if (encoding == PayloadEncoding.HEX) {
            return formatHex(parseHex(text));
        }
        if (looksLikeHex(text)) {
            return encodeFrame(parseHex(text), encoding);
        }
        return text;
    }

    private static String encodeFrame(byte[] frame, PayloadEncoding encoding) {
        if (encoding == PayloadEncoding.BINARY) {
            return new String(frame, StandardCharsets.ISO_8859_1);
        }
        return formatHex(frame);
    }

    private static byte[] decodeFrame(String text, PayloadEncoding encoding) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        if (encoding == PayloadEncoding.BINARY) {
            if (looksLikeHex(text)) {
                return parseHex(text);
            }
            return text.getBytes(StandardCharsets.ISO_8859_1);
        }
        return parseHex(text);
    }
}
