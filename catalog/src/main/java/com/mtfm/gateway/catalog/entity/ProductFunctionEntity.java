package com.mtfm.gateway.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mtfm.gateway.catalog.mybatis.JsonColumnTypeHandler;

/**
 * 产品功能实体：挂在产品下的逻辑功能定义。
 *
 * <p>{@code optionSchema} 存功能参数 JSON（表单默认值）；
 * {@code protocolMapping} 存协议层映射 JSON（如寄存器 offset，不含从站号）。
 */
@TableName(value = "gw_product_function", autoResultMap = true)
public class ProductFunctionEntity {

    /** 主键（雪花 ID）。 */
    @TableId
    private String id;

    /** 所属产品主键，对应 {@link ProductEntity#id}。 */
    private String productId;

    /** 功能业务 ID，流水线 COMMAND 的 functionId，如 {@code fn.read}。 */
    private String functionId;

    /** 访问类型：{@code READ} / {@code WRITE}。 */
    private String accessType;

    /**
     * 读写权限码，与 {@link com.mtfm.gateway.spi.model.AccessPermission} 对齐。
     * <p>READ=1，WRITE=2，可按位组合。
     */
    private Integer accessPermission;

    /**
     * 南向能力类型，如 {@code MODBUS} / {@code MQTT}。
     * <p>用于拉取能力侧 FunctionTemplate，做表单预填与校验。
     */
    private String capabilityType;

    /**
     * 功能参数 schema / 默认值，JSON 字符串。
     * <p>例：{@code {"area":"HOLDING","offset":0,"quantity":1}}。
     */
    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private String optionSchema;

    /**
     * 协议映射 JSON，不含寻址片（从站号 / topic 段等）。
     * <p>例：{@code {"holdingOffset":100}}。
     */
    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private String protocolMapping;

    /** 同产品内排序，数值越小越靠前。 */
    private Integer sortIndex;

    /** 写访问模式：VALUE / STRUCT。 */
    private String writeAccessType;

    /** 功能说明（可覆盖能力模板 description）。 */
    private String description;

    /** 载荷模式：VALUE / STRUCT。 */
    private String payloadMode;

    /** FieldNode 树 JSON。 */
    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private String structSchema;

    /** ValueMapping 列表 JSON。 */
    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private String valueMappings;

    /** MQTT 发布 topic slot。 */
    private String publishTopicSlot;

    /** MQTT 订阅 topic slot。 */
    private String subscribeTopicSlot;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public Integer getAccessPermission() {
        return accessPermission;
    }

    public void setAccessPermission(Integer accessPermission) {
        this.accessPermission = accessPermission;
    }

    public String getCapabilityType() {
        return capabilityType;
    }

    public void setCapabilityType(String capabilityType) {
        this.capabilityType = capabilityType;
    }

    public String getOptionSchema() {
        return optionSchema;
    }

    public void setOptionSchema(String optionSchema) {
        this.optionSchema = optionSchema;
    }

    public String getProtocolMapping() {
        return protocolMapping;
    }

    public void setProtocolMapping(String protocolMapping) {
        this.protocolMapping = protocolMapping;
    }

    public Integer getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(Integer sortIndex) {
        this.sortIndex = sortIndex;
    }

    public String getWriteAccessType() {
        return writeAccessType;
    }

    public void setWriteAccessType(String writeAccessType) {
        this.writeAccessType = writeAccessType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPayloadMode() {
        return payloadMode;
    }

    public void setPayloadMode(String payloadMode) {
        this.payloadMode = payloadMode;
    }

    public String getStructSchema() {
        return structSchema;
    }

    public void setStructSchema(String structSchema) {
        this.structSchema = structSchema;
    }

    public String getValueMappings() {
        return valueMappings;
    }

    public void setValueMappings(String valueMappings) {
        this.valueMappings = valueMappings;
    }

    public String getPublishTopicSlot() {
        return publishTopicSlot;
    }

    public void setPublishTopicSlot(String publishTopicSlot) {
        this.publishTopicSlot = publishTopicSlot;
    }

    public String getSubscribeTopicSlot() {
        return subscribeTopicSlot;
    }

    public void setSubscribeTopicSlot(String subscribeTopicSlot) {
        this.subscribeTopicSlot = subscribeTopicSlot;
    }
}
