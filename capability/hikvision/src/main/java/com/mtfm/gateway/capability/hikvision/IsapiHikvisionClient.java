package com.mtfm.gateway.capability.hikvision;

import com.mtfm.gateway.capability.hikvision.isapi.HikvisionHttpClient;
import com.mtfm.gateway.capability.hikvision.isapi.HikvisionJson;
import com.mtfm.gateway.capability.hikvision.isapi.ImageSupport;
import com.mtfm.gateway.capability.hikvision.isapi.model.ResponseStatus;
import com.mtfm.gateway.capability.hikvision.isapi.model.UserInfo;
import com.mtfm.gateway.capability.hikvision.isapi.model.UserInfoDelCond;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 海康 ISAPI 真实客户端，逻辑移植自旧网关 {@code DeviceGatewayHikvisionAccessControlService}。
 */
public final class IsapiHikvisionClient implements HikvisionClient {

    private static final Logger log = LoggerFactory.getLogger(IsapiHikvisionClient.class);
    private static final int ALL_DOORS = 65535;

    private final HikvisionChannelConfig config;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public IsapiHikvisionClient(HikvisionChannelConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public String channelId() {
        return config.channelId();
    }

    @Override
    public boolean remoteControlDoor(String deviceSerialNo, String target, String command) {
        ensureOpen();
        String door = (target == null || target.isBlank()) ? String.valueOf(ALL_DOORS) : target;
        Map<String, Object> body = Map.of("RemoteControlDoor", Map.of("cmd", command));
        String url = basePath()
                + "/ISAPI/AccessControl/RemoteControl/door/" + door + "?format=json&devIndex=" + deviceSerialNo;
        String response = sendPut(url, HikvisionJson.toJson(body), "remote control door");
        int statusCode = HikvisionJson.getAsInt(response, "statusCode");
        if (statusCode != 1) {
            String statusString = HikvisionJson.getAsString(response, "statusString");
            throw new HikvisionAccessException("远程控门失败: " + statusString, false);
        }
        return true;
    }

    @Override
    public boolean setUpUser(String deviceSerialNo, String employeeNo, String name,
            String beginTime, String endTime, String imgStr) {
        ensureOpen();
        setUser(deviceSerialNo, employeeNo, name, beginTime, endTime, imgStr);
        return true;
    }

    @Override
    public boolean modifyUser(String deviceSerialNo, String employeeNo, String name,
            String beginTime, String endTime, String imgStr) {
        ensureOpen();
        if (deleteUser(deviceSerialNo, List.of(employeeNo))) {
            return setUpUser(deviceSerialNo, employeeNo, name, beginTime, endTime, imgStr);
        }
        return false;
    }

    @Override
    public boolean deleteUser(String deviceSerialNo, List<String> employeeNoList) {
        ensureOpen();
        UserInfoDelCond cond = UserInfoDelCond.fromEmployeeNos(employeeNoList);
        String body = HikvisionJson.wrapJson(HikvisionJson.toJson(cond), "UserInfoDetail");
        String url = basePath()
                + "/ISAPI/AccessControl/UserInfoDetail/Delete?format=json&devIndex=" + deviceSerialNo;
        return handleResponse(sendPut(url, body, "delete user"), "delete user");
    }

    @Override
    public boolean setUpCard(String deviceSerialNo, String employeeNo, String cardNo, String cardType) {
        throw new HikvisionAccessException("setUpCard 当前设备网关不支持", false);
    }

    @Override
    public boolean modifyCard(String deviceSerialNo, String employeeNo, String cardNo, String cardType) {
        throw new HikvisionAccessException("modifyCard 当前设备网关不支持", false);
    }

    @Override
    public boolean deleteCard(String deviceSerialNo, List<String> cardNoList) {
        throw new HikvisionAccessException("deleteCard 当前设备网关不支持", false);
    }

    @Override
    public void close() {
        closed.set(true);
    }

    /**
     * 设置用户
     * 
     * @param deviceSerialNo 设备序列号
     * @param employeeNo     员工编号
     * @param name           姓名
     * @param beginTime      开始时间
     * @param endTime        结束时间
     * @param imgStr         图片字符串
     */
    private void setUser(String deviceSerialNo, String employeeNo, String name,
            String beginTime, String endTime, String imgStr) {
        UserInfo userInfo = UserInfo.of(employeeNo, name, beginTime, endTime);
        Map<String, Object> body = Map.of("UserInfo", List.of(userInfo));
        String url = basePath()
                + "/ISAPI/AccessControl/UserInfo/Record?format=json&devIndex=" + deviceSerialNo;
        String response = sendPost(url, HikvisionJson.toJson(body), "set up user");
        if (handleResponse(response, "set up user")) {
            setFace(deviceSerialNo, employeeNo, imgStr);
        }
    }

    /**
     * 设置人脸
     * 
     * @param deviceSerialNo 设备序列号
     * @param employeeNo     员工编号
     * @param imgStr         图片字符串
     */
    private void setFace(String deviceSerialNo, String employeeNo, String imgStr) {
        if (imgStr == null || imgStr.isBlank()) {
            log.debug("未提供 imgStr，跳过人脸下发");
            return;
        }
        String filename = "temp_face_" + System.currentTimeMillis() + "_"
                + sanitizeFileName(employeeNo) + ".jpg";
        File tempFile = convertImgStrToFile(imgStr, filename);
        try {
            String url = basePath()
                    + "/ISAPI/Intelligent/FDLib/FDSetUp?format=json&devIndex=" + deviceSerialNo;
            String faceJson = HikvisionJson.toJson(Map.of("FPID", employeeNo));
            String response = HikvisionHttpClient.putMultipart(
                    url, faceJson, tempFile, config.username(), config.password());
            handleResponse(response, "set up face");
        } catch (IOException ex) {
            throw new HikvisionAccessException("下发人脸失败: " + ex.getMessage(), ex, true);
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    /**
     * 将图片字符串转换为文件
     * 
     * @param imgStr   图片字符串
     * @param filePath 文件路径
     * @return 文件
     */
    private File convertImgStrToFile(String imgStr, String filePath) {
        byte[] imgBytes = ImageSupport.resolveImageBytes(imgStr);
        File tempFile = new File(System.getProperty("java.io.tmpdir"), filePath);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(imgBytes);
        } catch (IOException ex) {
            throw new HikvisionAccessException("写入临时图片失败: " + ex.getMessage(), ex, false);
        }
        return tempFile;
    }

    /**
     * 处理响应
     * 
     * @param response      响应
     * @param operationName 操作名称
     * @return 是否成功
     */
    private boolean handleResponse(String response, String operationName) {
        if (response != null && response.contains("employeeNoAlreadyExist")) {
            return true;
        }
        ResponseStatus status = HikvisionJson.parse(response, ResponseStatus.class);
        if (status != null && !status.isSuccess()) {
            throw new HikvisionAccessException(operationName + " 失败: " + status.getStatusString(), false);
        }
        log.debug("海康 ISAPI 成功: {}", operationName);
        return true;
    }

    /**
     * 发送 POST 请求
     * 
     * @param url           请求 URL
     * @param body          请求体
     * @param operationName 操作名称
     * @return 响应
     */
    private String sendPost(String url, String body, String operationName) {
        try {
            return HikvisionHttpClient.post(url, body, config.username(), config.password());
        } catch (IOException ex) {
            throw new HikvisionAccessException(operationName + " 请求失败: " + ex.getMessage(), ex, true);
        }
    }

    /**
     * 发送 PUT 请求
     * 
     * @param url           请求 URL
     * @param body          请求体
     * @param operationName 操作名称
     * @return 响应
     */
    private String sendPut(String url, String body, String operationName) {
        try {
            return HikvisionHttpClient.put(url, body, config.username(), config.password());
        } catch (IOException ex) {
            throw new HikvisionAccessException(operationName + " 请求失败: " + ex.getMessage(), ex, true);
        }
    }

    /**
     * 获取基础路径
     * 
     * @return 基础路径
     */
    private String basePath() {
        return config.baseUrl();
    }

    /**
     * 确保客户端打开
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw new HikvisionAccessException("客户端已关闭: " + config.channelId(), false);
        }
    }

    /**
     * 清理临时文件
     * 
     * @param tempFile 临时文件
     */
    private static void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                Files.delete(tempFile.toPath());
            } catch (IOException ex) {
                log.warn("删除临时文件失败: {}", tempFile.getAbsolutePath());
                tempFile.deleteOnExit();
            }
        }
    }

    /**
     * 清理临时文件
     * 
     * @param fileName 文件名
     * @return 清理后的文件名
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        return fileName.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
