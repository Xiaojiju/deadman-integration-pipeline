package com.mtfm.gateway.capability.hikvision;

import com.mtfm.gateway.spi.model.CapabilityDescriptor;
import com.mtfm.gateway.spi.model.FunctionCatalogMode;
import com.mtfm.gateway.spi.model.FunctionTemplate;
import com.mtfm.gateway.spi.model.SchemaField;

import java.util.List;

/**
 * 南向海康 ISAPI 门禁能力常量。
 *
 * <p>
 * 功能集固定，对齐旧网关 {@code ServiceType}：远程控门、人员与卡片增删改。
 * Channel（connection）= ISAPI 主机连接；Address = deviceSerialNo（devIndex）。
 */
public final class HikvisionCapability {

    /**
     * 能力类型
     */
    public static final String TYPE = "HIKVISION_ENTRANCE";
    /**
     * 远程控门函数
     */

    public static final String FN_REMOTE_CONTROL_DOOR = "remoteControlDoor";
    /**
     * 新增人员函数
     */
    public static final String FN_SET_UP_USER = "setUpUser";
    /**
     * 修改人员函数
     */
    public static final String FN_MODIFY_USER = "modifyUser";
    /**
     * 删除人员函数
     */
    public static final String FN_DELETE_USER = "deleteUser";
    /**
     * 新增卡片函数
     */
    public static final String FN_SET_UP_CARD = "setUpCard";
    /**
     * 修改卡片函数
     */
    public static final String FN_MODIFY_CARD = "modifyCard";
    /**
     * 删除卡片函数
     */
    public static final String FN_DELETE_CARD = "deleteCard";

    /**
     * 用户参数
     */
    private static final List<SchemaField> USER_PARAMS = List.of(
            SchemaField.required("employeeNo", "string", "员工编号"),
            SchemaField.required("name", "string", "姓名"),
            SchemaField.optional("beginTime", "string", "生效开始时间（ISO-8601）"),
            SchemaField.optional("endTime", "string", "生效结束时间（ISO-8601）"),
            SchemaField.optional("imgStr", "string", "人脸图片 Base64"));

    /**
     * 卡片参数
     */
    private static final List<SchemaField> CARD_PARAMS = List.of(
            SchemaField.required("employeeNo", "string", "员工编号"),
            SchemaField.required("cardNo", "string", "卡号"),
            SchemaField.optional("cardType", "string", "卡类型"));

    /**
     * 能力描述符
     */
    public static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(
            TYPE,
            List.of(
                    SchemaField.required("host", "string", "ISAPI 主机"),
                    SchemaField.optional("port", "int", "端口", 80),
                    SchemaField.optional("username", "string", "用户名，凭证建议来自环境变量"),
                    SchemaField.optionalSecret("password", "密码，凭证建议来自环境变量")),
            List.of(SchemaField.required("deviceSerialNo", "string", "设备序列号（devIndex）")),
            List.of(
                    FunctionTemplate.of(FN_REMOTE_CONTROL_DOOR, "远程控门", "WRITE", List.of(
                            SchemaField.optional("target", "string", "门编号，空则全开", "65535"),
                            SchemaField.choice("command", "控门指令", true, "open",
                                    List.of("open", "close")))),
                    FunctionTemplate.of(FN_SET_UP_USER, "新增人员", "WRITE", USER_PARAMS),
                    FunctionTemplate.of(FN_MODIFY_USER, "修改人员", "WRITE", USER_PARAMS),
                    FunctionTemplate.of(FN_DELETE_USER, "删除人员", "WRITE", List.of(
                            SchemaField.required("employeeNoList", "string", "员工编号列表，JSON 数组或逗号分隔"))),
                    FunctionTemplate.of(FN_SET_UP_CARD, "新增卡片", "WRITE", CARD_PARAMS),
                    FunctionTemplate.of(FN_MODIFY_CARD, "修改卡片", "WRITE", CARD_PARAMS),
                    FunctionTemplate.of(FN_DELETE_CARD, "删除卡片", "WRITE", List.of(
                            SchemaField.required("cardNoList", "string", "卡号列表，JSON 数组或逗号分隔")))),
            FunctionCatalogMode.FIXED);

    private HikvisionCapability() {
    }
}
