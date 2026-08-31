package com.mtfm.gateway.spi.model;

import java.util.List;

/**
 * 能力预置的功能模板。配置产品功能时可按此装配表单。
 *
 * <p>使用示例：
 * <pre>{@code
 * FunctionTemplate read = FunctionTemplate.of("fn.read", "READ", List.of(
 *         SchemaField.required("offset", "int", "寄存器偏移"),
 *         SchemaField.required("count", "int", "读取数量")));
 * }</pre>
 *
 * @param functionId       功能 ID
 * @param description      功能说明，供配置表单展示
 * @param accessType       访问类型（READ / WRITE）
 * @param accessPermission 读写权限码
 * @param parameters       功能参数字段
 */
public record FunctionTemplate(
        String functionId,
        String description,
        String accessType,
        int accessPermission,
        List<SchemaField> parameters
) {

    public FunctionTemplate {
        if (functionId == null || functionId.isBlank()) {
            throw new IllegalArgumentException("functionId 不能为空");
        }
        if (description == null || description.isBlank()) {
            description = functionId;
        }
        if (accessType == null || accessType.isBlank()) {
            accessType = "WRITE";
        }
        if (accessPermission <= 0) {
            accessPermission = AccessPermission.WRITE.code();
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    /** 按访问类型与参数字段创建模板，description 默认等于 functionId。 */
    public static FunctionTemplate of(String functionId, String accessType, List<SchemaField> parameters) {
        return of(functionId, functionId, accessType, parameters);
    }

    /** 按说明、访问类型与参数字段创建模板。 */
    public static FunctionTemplate of(String functionId, String description, String accessType,
            List<SchemaField> parameters) {
        int permission = "READ".equalsIgnoreCase(accessType)
                ? AccessPermission.READ.code()
                : AccessPermission.WRITE.code();
        return new FunctionTemplate(functionId, description, accessType, permission, parameters);
    }
}
