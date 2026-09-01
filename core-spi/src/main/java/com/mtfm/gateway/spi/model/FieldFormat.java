package com.mtfm.gateway.spi.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Schema 字段 UI / 采值约束（与 {@link FieldType} 解耦）。
 *
 * <p>告诉可视化如何采集，落库值仍由 type 解释（如 IMAGE_BASE64 最终仍是 string Base64）。
 */
public enum FieldFormat {

    /** 无特殊约束，按 {@link FieldType} 默认控件。 */
    NONE("none"),
    /** ISO-8601 日期时间；UI 用 datetime 选择器。 */
    DATETIME_ISO8601("datetime_iso8601"),
    /** 图片 Base64；UI 选本地文件后转 Base64 再提交。 */
    IMAGE_BASE64("image_base64"),
    /** 文本列表（逗号分隔或 JSON 数组字符串）；UI 用多行文本。 */
    TEXT_LIST("text_list");

    private final String code;

    FieldFormat(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** 解析 wire；未知回落 {@link #NONE}。 */
    @JsonCreator
    public static FieldFormat from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String key = raw.trim().toLowerCase();
        return switch (key) {
            case "none", "" -> NONE;
            case "datetime_iso8601", "datetime", "date-time", "iso8601" -> DATETIME_ISO8601;
            case "image_base64", "image", "base64_image" -> IMAGE_BASE64;
            case "text_list", "list", "csv" -> TEXT_LIST;
            default -> NONE;
        };
    }
}
