package com.mtfm.gateway.capability.hikvision.isapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 海康 ISAPI 删除用户条件，对齐旧网关 {@code UserInfoDelCond}。
 */
public final class UserInfoDelCond {

    /**
     * 员工编号列表
     */
    @JsonProperty("EmployeeNoList")
    private List<EmployeeNoDelCond> employeeNoList;
    /**
     * 模式
     */
    private String mode = "byEmployeeNo";

    /**
     * 从员工编号列表创建删除条件
     * 
     * @param employeeNos 员工编号列表
     * @return 删除条件
     */
    public static UserInfoDelCond fromEmployeeNos(List<String> employeeNos) {
        UserInfoDelCond cond = new UserInfoDelCond();
        if (employeeNos == null || employeeNos.isEmpty()) {
            return cond;
        }
        cond.employeeNoList = employeeNos.stream().map(EmployeeNoDelCond::new).toList();
        return cond;
    }

    /**
     * 获取员工编号列表
     * 
     * @return 员工编号列表
     */
    public List<EmployeeNoDelCond> getEmployeeNoList() {
        return employeeNoList;
    }

    /**
     * 获取模式
     * 
     * @return 模式
     */
    public String getMode() {
        return mode;
    }

    /**
     * 员工编号删除条件
     */
    public static final class EmployeeNoDelCond {
        /**
         * 员工编号
         */
        private final String employeeNo;

        /**
         * 创建员工编号删除条件
         * 
         * @param employeeNo 员工编号
         */
        public EmployeeNoDelCond(String employeeNo) {
            this.employeeNo = employeeNo;
        }

        /**
         * 获取员工编号
         * 
         * @return 员工编号
         */
        public String getEmployeeNo() {
            return employeeNo;
        }
    }
}
