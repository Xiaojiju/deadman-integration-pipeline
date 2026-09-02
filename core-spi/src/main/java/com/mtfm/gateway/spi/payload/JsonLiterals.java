package com.mtfm.gateway.spi.payload;

import java.util.ArrayList;
import java.util.List;

/** 将数组字段上的 JSON 文本常量（如 {@code ["open","1"]}）还原为 List。 */
final class JsonLiterals {

    private JsonLiterals() {
    }

    static Object coerceConstant(Object raw, boolean arrayField) {
        if (!arrayField || !(raw instanceof String text)) {
            return raw;
        }
        String trimmed = text.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            return raw;
        }
        List<Object> parsed = parseJsonArray(trimmed);
        return parsed == null ? raw : parsed;
    }

    private static List<Object> parseJsonArray(String text) {
        List<Object> items = new ArrayList<>();
        int i = 1;
        int end = text.length() - 1;
        while (i < end) {
            while (i < end && (text.charAt(i) == ',' || Character.isWhitespace(text.charAt(i)))) {
                i++;
            }
            if (i >= end) {
                break;
            }
            if (text.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < end) {
                    char c = text.charAt(i);
                    if (c == '\\' && i + 1 < end) {
                        sb.append(text.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == '"') {
                        i++;
                        break;
                    }
                    sb.append(c);
                    i++;
                }
                items.add(sb.toString());
                continue;
            }
            int start = i;
            while (i < end && text.charAt(i) != ',') {
                i++;
            }
            String token = text.substring(start, i).trim();
            if (!token.isEmpty() && !"null".equals(token)) {
                items.add(token);
            }
        }
        return items;
    }
}
