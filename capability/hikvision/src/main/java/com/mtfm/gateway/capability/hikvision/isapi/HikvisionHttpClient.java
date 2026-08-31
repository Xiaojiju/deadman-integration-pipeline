package com.mtfm.gateway.capability.hikvision.isapi;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * 海康 ISAPI HTTP 客户端，Digest 认证，对齐旧网关 {@code HikvisionHttpRequest}。
 */
public final class HikvisionHttpClient {

    private static final Logger log = LoggerFactory.getLogger(HikvisionHttpClient.class);
    private static final AuthScope ANY = new AuthScope(null, null, -1, null, null);

    private HikvisionHttpClient() {
    }

    /**
     * POST 请求。
     * 
     * @param url      请求 URL
     * @param body     请求体
     * @param username 用户名
     * @param password 密码
     * @return 响应体
     * @throws IOException 请求失败
     */
    public static String post(String url, String body, String username, String password) throws IOException {
        log.debug("海康 ISAPI POST: {}", url);
        HttpPost request = new HttpPost(url);
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        if (body != null && !body.isBlank()) {
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        }
        return execute(request, username, password);
    }

    /**
     * PUT 请求。
     * 
     * @param url      请求 URL
     * @param body     请求体
     * @param username 用户名
     * @param password 密码
     * @return 响应体
     * @throws IOException 请求失败
     */
    public static String put(String url, String body, String username, String password) throws IOException {
        log.debug("海康 ISAPI PUT: {}", url);
        HttpPut request = new HttpPut(url);
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        if (body != null && !body.isBlank()) {
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        }
        return execute(request, username, password);
    }

    /**
     * PUT multipart 请求。
     * 
     * @param url        请求 URL
     * @param jsonParams JSON 参数
     * @param imageFile  图片文件
     * @param username   用户名
     * @param password   密码
     * @return 响应体
     * @throws IOException 请求失败
     */
    public static String putMultipart(String url, String jsonParams, File imageFile,
            String username, String password) throws IOException {
        log.debug("海康 ISAPI PUT multipart: {}", url);
        HttpPut request = new HttpPut(url);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.LEGACY);
        builder.addTextBody("FPID", jsonParams, ContentType.APPLICATION_JSON);
        builder.addBinaryBody("FaceImage", imageFile, ContentType.IMAGE_JPEG, imageFile.getName());
        request.setEntity(builder.build());
        return execute(request, username, password);
    }

    /**
     * 执行请求。
     * 
     * @param request  请求
     * @param username 用户名
     * @param password 密码
     * @return 响应体
     * @throws IOException 请求失败
     */
    private static String execute(org.apache.hc.core5.http.ClassicHttpRequest request,
            String username, String password) throws IOException {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        Credentials credentials = new UsernamePasswordCredentials(username, password.toCharArray());
        credentialsProvider.setCredentials(ANY, credentials);
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build()) {
            return client.execute(request, HikvisionHttpClient::handleResponse);
        }
    }

    /**
     * 处理响应。
     * 
     * @param response 响应
     * @return 响应体
     * @throws IOException 响应体解析失败
     */
    private static String handleResponse(ClassicHttpResponse response) throws IOException {
        int statusCode = response.getCode();
        String body = response.getEntity() == null ? "" : readEntity(response);
        if (statusCode >= 400) {
            log.warn("海康 ISAPI HTTP {}: {}", statusCode, body);
        }
        return body;
    }

    /**
     * 读取响应体。
     * 
     * @param response 响应
     * @return 响应体
     * @throws IOException 响应体解析失败
     */
    private static String readEntity(ClassicHttpResponse response) throws IOException {
        try {
            return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        } catch (ParseException ex) {
            throw new IOException("响应体解析失败", ex);
        }
    }
}
