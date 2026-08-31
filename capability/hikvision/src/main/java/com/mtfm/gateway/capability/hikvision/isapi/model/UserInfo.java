package com.mtfm.gateway.capability.hikvision.isapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 海康 ISAPI 用户信息，对齐旧网关 {@code UserInfo}。
 */
public final class UserInfo {

    /**
     * 员工编号
     */
    private String employeeNo;
    /**
     * 姓名
     */
    private String name;
    /**
     * 有效期
     */
    @JsonProperty("Valid")
    private Valid valid;

    /**
     * 创建用户信息
     * 
     * @param employeeNo 员工编号
     * @param name       姓名
     * @param beginTime  开始时间
     * @param endTime    结束时间
     * @return 用户信息
     */
    public static UserInfo of(String employeeNo, String name, String beginTime, String endTime) {
        UserInfo userInfo = new UserInfo();
        userInfo.employeeNo = employeeNo;
        userInfo.name = name;
        Valid valid = new Valid();
        valid.beginTime = parseDateTime(beginTime);
        valid.endTime = parseDateTime(endTime);
        userInfo.valid = valid;
        return userInfo;
    }

    /**
     * 解析日期时间
     * 
     * @param value 日期时间字符串
     * @return 日期时间
     */
    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    /**
     * 获取员工编号
     * 
     * @return 员工编号
     */
    public String getEmployeeNo() {
        return employeeNo;
    }

    /**
     * 获取姓名
     * 
     * @return 姓名
     */
    public String getName() {
        return name;
    }

    /**
     * 获取有效期
     * 
     * @return 有效期
     */
    public Valid getValid() {
        return valid;
    }

    /**
     * 有效期
     */
    public static final class Valid {
        /**
         * 开始时间
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime beginTime;
        /**
         * 结束时间
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endTime;
        /**
         * 时间类型
         */
        private String timeType = "local";
        /**
         * 是否启用
         */
        private boolean enable = true;

        /**
         * 获取开始时间
         * 
         * @return 开始时间
         */
        public LocalDateTime getBeginTime() {
            return beginTime;
        }

        /**
         * 获取结束时间
         * 
         * @return 结束时间
         */
        public LocalDateTime getEndTime() {
            return endTime;
        }

        /**
         * 获取时间类型
         * 
         * @return 时间类型
         */
        public String getTimeType() {
            return timeType;
        }

        /**
         * 是否启用
         * 
         * @return 是否启用
         */
        public boolean isEnable() {
            return enable;
        }
    }
}
