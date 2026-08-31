package com.mtfm.gateway.capability.hikvision.isapi;

import com.mtfm.gateway.capability.hikvision.HikvisionAccessException;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Iterator;

/**
 * 人脸图片下载与压缩，对齐旧网关 {@code HttpFetchUtils} + {@code ImageCompressor}。
 */
public final class ImageSupport {

    private static final long MAX_FILE_SIZE = 200 * 1024;

    private ImageSupport() {
    }

    public static byte[] resolveImageBytes(String imgStr) {
        if (imgStr == null || imgStr.isBlank()) {
            throw new HikvisionAccessException("imgStr 不能为空", false);
        }
        byte[] bytes = imgStr.startsWith("http://") || imgStr.startsWith("https://")
                ? downloadImage(imgStr)
                : decodeBase64(imgStr);
        try {
            bytes = compressImageToDimensions(bytes, 450, 600);
            if (bytes.length > MAX_FILE_SIZE) {
                bytes = compressImageToSize(bytes, MAX_FILE_SIZE);
            }
        } catch (IOException ex) {
            throw new HikvisionAccessException("图片压缩失败: " + ex.getMessage(), ex, false);
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new HikvisionAccessException("图片超过 200KB 限制", false);
        }
        return bytes;
    }

    private static byte[] decodeBase64(String imgStr) {
        try {
            String payload = imgStr.contains(",") ? imgStr.substring(imgStr.indexOf(',') + 1) : imgStr;
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            throw new HikvisionAccessException("imgStr Base64 解析失败", ex, false);
        }
    }

    private static byte[] downloadImage(String imageUrl) {
        try {
            trustAllCertificates();
            HttpURLConnection connection = (HttpURLConnection) URI.create(imageUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (InputStream inputStream = connection.getInputStream()) {
                return inputStream.readAllBytes();
            }
        } catch (Exception ex) {
            throw new HikvisionAccessException("下载图片失败: " + imageUrl, ex, true);
        }
    }

    private static byte[] compressImageToDimensions(byte[] originalBytes, int maxWidth, int maxHeight)
            throws IOException {
        BufferedImage image = toCompatibleImage(ImageIO.read(new ByteArrayInputStream(originalBytes)));
        Dimension target = calculateTargetDimensions(image.getWidth(), image.getHeight(), maxWidth, maxHeight);
        BufferedImage resized = resizeImage(image, target);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resized, "jpeg", baos);
        return baos.toByteArray();
    }

    private static byte[] compressImageToSize(byte[] originalBytes, long maxSize) throws IOException {
        if (originalBytes.length <= maxSize) {
            return originalBytes;
        }
        BufferedImage image = toCompatibleImage(ImageIO.read(new ByteArrayInputStream(originalBytes)));
        Dimension size = calculateOptimalDimensions(image, maxSize);
        BufferedImage resized = resizeImage(image, size);
        float quality = 0.8f;
        while (quality > 0.1f) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                writer.setOutput(ios);
                var param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new javax.imageio.IIOImage(resized, null, null), param);
                ios.close();
                writer.dispose();
                byte[] compressed = baos.toByteArray();
                if (compressed.length <= maxSize) {
                    return compressed;
                }
            }
            quality -= 0.1f;
        }
        return originalBytes;
    }

    private static BufferedImage toCompatibleImage(BufferedImage original) {
        if (original.getType() == BufferedImage.TYPE_INT_RGB || original.getType() == BufferedImage.TYPE_INT_ARGB) {
            return original;
        }
        BufferedImage compatible = new BufferedImage(original.getWidth(), original.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = compatible.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();
        return compatible;
    }

    private static BufferedImage resizeImage(BufferedImage original, Dimension size) {
        BufferedImage resized = new BufferedImage(size.width, size.height, original.getType());
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, size.width, size.height, null);
        g2d.dispose();
        return resized;
    }

    private static Dimension calculateTargetDimensions(int originalWidth, int originalHeight,
            int maxWidth, int maxHeight) {
        int targetWidth = originalWidth;
        int targetHeight = originalHeight;
        if (originalWidth > maxWidth) {
            targetWidth = maxWidth;
            targetHeight = (int) ((float) originalHeight / originalWidth * targetWidth);
        }
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight;
            targetWidth = (int) ((float) originalWidth / originalHeight * targetHeight);
        }
        return new Dimension(targetWidth, targetHeight);
    }

    private static Dimension calculateOptimalDimensions(BufferedImage image, long maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        double sizeRatio = Math.sqrt((double) maxSize / (width * height * 3));
        return new Dimension(Math.max((int) (width * sizeRatio), 100), Math.max((int) (height * sizeRatio), 100));
    }

    private static void trustAllCertificates() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HostnameVerifier allHostsValid = (hostname, session) -> true;
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }
}
