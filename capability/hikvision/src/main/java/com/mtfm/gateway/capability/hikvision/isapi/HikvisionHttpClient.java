package com.mtfm.gateway.capability.hikvision.isapi;

import com.mtfm.gateway.capability.hikvision.HikvisionAccessException;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * 海康 ISAPI HTTP 客户端，Digest 认证，对齐旧网关 {@code HikvisionHttpRequest}。
 *
 * <p>共享连接池；凭证按请求放入 {@link HttpClientContext}，避免多通道 Digest 串台。
 */
public final class HikvisionHttpClient {

    private static final Logger log = LoggerFactory.getLogger(HikvisionHttpClient.class);
    private static final AuthScope ANY = new AuthScope(null, null, -1, null, null);
    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(5);
    private static final Timeout SOCKET_TIMEOUT = Timeout.ofSeconds(10);

    private static final CloseableHttpClient CLIENT = createClient();

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
     * 关闭共享连接池。由 {@link com.mtfm.gateway.capability.hikvision.HikvisionExecutor#close()} 在进程退出时调用。
     */
    public static void shutdown() {
        try {
            CLIENT.close();
        } catch (IOException ex) {
            log.warn("关闭海康 HttpClient: {}", ex.getMessage());
        }
    }

    private static CloseableHttpClient createClient() {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(64)
                .setMaxConnPerRoute(16)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(CONNECT_TIMEOUT)
                        .setSocketTimeout(SOCKET_TIMEOUT)
                        .build())
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                .setResponseTimeout(SOCKET_TIMEOUT)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .build();
    }

    private static String execute(org.apache.hc.core5.http.ClassicHttpRequest request,
            String username, String password) throws IOException {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        char[] secret = password == null ? new char[0] : password.toCharArray();
        Credentials credentials = new UsernamePasswordCredentials(username == null ? "" : username, secret);
        credentialsProvider.setCredentials(ANY, credentials);
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credentialsProvider);
        return CLIENT.execute(request, context, HikvisionHttpClient::handleResponse);
    }

    static String handleResponse(ClassicHttpResponse response) throws IOException {
        int statusCode = response.getCode();
        String body = response.getEntity() == null ? "" : readEntity(response);
        if (statusCode >= 400) {
            boolean retryable = statusCode >= 500 || statusCode == 429;
            throw new HikvisionAccessException("海康 ISAPI HTTP " + statusCode + ": " + body, retryable);
        }
        return body;
    }

    private static String readEntity(ClassicHttpResponse response) throws IOException {
        try {
            return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        } catch (ParseException ex) {
            throw new IOException("响应体解析失败", ex);
        }
    }
}
