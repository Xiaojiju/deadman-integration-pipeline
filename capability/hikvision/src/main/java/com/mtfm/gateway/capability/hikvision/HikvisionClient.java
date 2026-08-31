package com.mtfm.gateway.capability.hikvision;

import java.util.List;

/**
 * 海康门禁 ISAPI 客户端。关闭由通道引用计数归零触发。
 *
 * <p>
 * 方法语义对齐旧网关 {@code AccessControlService}，由 {@link HikvisionExecutor} 按
 * functionId 分发调用。
 */
public interface HikvisionClient {

    String channelId();

    /**
     * 远程控门。target 为空时由实现方默认全开（65535）。
     * 
     * @param deviceSerialNo 设备序列号
     * @param target         目标
     * @param command        命令
     * @return
     * @return 是否成功
     */
    boolean remoteControlDoor(String deviceSerialNo, String target, String command);

    /**
     * 新增人员。
     * 
     * @param deviceSerialNo 设备序列号
     * @param employeeNo     员工编号
     * @param name           姓名
     * @param beginTime      开始时间
     * @param endTime        结束时间
     * @param imgStr         图片字符串
     * @return 是否成功
     */
    boolean setUpUser(String deviceSerialNo, String employeeNo, String name,
            String beginTime, String endTime, String imgStr);

    /**
     * 修改人员（先删后增）。
     * 
     * @param deviceSerialNo 设备序列号
     * @param employeeNo     员工编号
     * @param name           姓名
     * @param beginTime      开始时间
     * @param endTime        结束时间
     * @param imgStr         图片字符串
     * @return 是否成功
     */
    boolean modifyUser(String deviceSerialNo, String employeeNo, String name,
            String beginTime, String endTime, String imgStr);

    /**
     * 删除人员。
     * 
     * @param deviceSerialNo 设备序列号
     * @param employeeNoList 员工编号列表
     * @return 是否成功
     */
    boolean deleteUser(String deviceSerialNo, List<String> employeeNoList);

    /**
     * 新增卡片。
     * 
     * @param deviceSerialNo 设备序列号
     * @param employeeNo     员工编号
     * @param cardNo         卡片编号
     * @param cardType       卡片类型
     * @return 是否成功
     */
    boolean setUpCard(String deviceSerialNo, String employeeNo, String cardNo, String cardType);

    /**
     * 修改卡片。
     * 
     * @param deviceSerialNo 设备序列号
     * @param employeeNo     员工编号
     * @param cardNo         卡片编号
     * @param cardType       卡片类型
     * @return 是否成功
     */
    boolean modifyCard(String deviceSerialNo, String employeeNo, String cardNo, String cardType);

    /**
     * 删除卡片。
     * 
     * @param deviceSerialNo 设备序列号
     * @param cardNoList     卡片编号列表
     * @return 是否成功
     */
    boolean deleteCard(String deviceSerialNo, List<String> cardNoList);

    /**
     * 关闭客户端。
     */
    void close();
}
