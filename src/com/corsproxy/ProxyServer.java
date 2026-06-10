package com.corsproxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ProxyServer {
    private HttpServer server;
    private final int port;
    private final String proxyPath;
    private final Supplier<File> htmlFileSupplier;
    private final Consumer<RequestLog> logConsumer;
    private final ExecutorService executor;
    private final HttpClient httpClient;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "host", "connection", "content-length", "expect", "upgrade", "via", "keep-alive", "accept-encoding"
    );

    public ProxyServer(int port, String proxyPath, Supplier<File> htmlFileSupplier, Consumer<RequestLog> logConsumer) {
        this.port = port;
        this.proxyPath = proxyPath.startsWith("/") ? proxyPath : "/" + proxyPath;
        this.htmlFileSupplier = htmlFileSupplier;
        this.logConsumer = logConsumer;
        this.executor = Executors.newCachedThreadPool();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(executor);

        // Root handler (serves app.html and relative static files)
        server.createContext("/", new StaticFileHandler());

        // Proxy handler
        server.createContext(proxyPath, new ProxyHandler());

        server.start();
        log("-", "SYSTEM", "Server started on port " + port, 200, 0);
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            executor.shutdownNow();
            log("-", "SYSTEM", "Server stopped", 200, 0);
        }
    }

    private void log(String method, String path, String targetUrl, int status, long durationMs) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        RequestLog logEntry = new RequestLog(timestamp, method, path, targetUrl, status, durationMs);
        logConsumer.accept(logEntry);
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long startTime = System.currentTimeMillis();
            String path = exchange.getRequestURI().getPath();
            
            // Normalize path
            if (path.equals("/") || path.equalsIgnoreCase("/index.html")) {
                path = "/app.html";
            }

            File fileToServe = null;
            File selectedFile = htmlFileSupplier.get();

            if (path.equalsIgnoreCase("/app.html")) {
                if (selectedFile != null && selectedFile.exists() && selectedFile.isFile()) {
                    fileToServe = selectedFile;
                } else {
                    // Check next to the jar
                    File localFile = new File("app.html");
                    if (localFile.exists() && localFile.isFile()) {
                        fileToServe = localFile;
                    }
                }
            } else {
                // For other relative files, attempt to serve relative to the served HTML file's parent directory
                File baseDir = null;
                if (selectedFile != null && selectedFile.exists()) {
                    baseDir = selectedFile.getParentFile();
                } else {
                    File localFile = new File("app.html");
                    if (localFile.exists()) {
                        baseDir = localFile.getParentFile();
                    }
                }
                
                if (baseDir != null) {
                    // Strip leading slash for relative lookup
                    String relativePath = path.substring(1);
                    File requestedFile = new File(baseDir, relativePath);
                    // Prevent path traversal attacks
                    if (requestedFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath()) && requestedFile.exists() && requestedFile.isFile()) {
                        fileToServe = requestedFile;
                    }
                }
            }

            // Write response headers and body
            if (fileToServe != null && fileToServe.exists()) {
                byte[] content = readFileBytes(fileToServe);
                String contentType = getContentType(fileToServe.getName());
                
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, content.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
                long duration = System.currentTimeMillis() - startTime;
                log(exchange.getRequestMethod(), path, fileToServe.getAbsolutePath(), 200, duration);
            } else {
                if (path.equalsIgnoreCase("/app.html")) {
                    // Serve a beautiful fallback page explaining that no HTML is selected
                    byte[] fallbackHtml = getFallbackHtmlPage().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(404, fallbackHtml.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(fallbackHtml);
                    }
                    long duration = System.currentTimeMillis() - startTime;
                    log(exchange.getRequestMethod(), path, "No HTML file found", 404, duration);
                } else {
                    // standard 404
                    byte[] notFound = "File Not Found".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(404, notFound.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(notFound);
                    }
                    long duration = System.currentTimeMillis() - startTime;
                    log(exchange.getRequestMethod(), path, "Not Found", 404, duration);
                }
            }
        }
    }

    private class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long startTime = System.currentTimeMillis();
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getRawQuery();
            String targetUrl = getQueryParam(query, "url");

            // Fallback: If no query param, check if the URL is appended directly in the path (e.g. /proxyhttps://...)
            if (targetUrl == null || targetUrl.trim().isEmpty()) {
                String rawPath = exchange.getRequestURI().getRawPath();
                if (rawPath.startsWith(proxyPath)) {
                    String remainder = rawPath.substring(proxyPath.length());
                    // Strip leading slash if present (e.g., /proxy/https://... -> https://...)
                    if (remainder.startsWith("/")) {
                        remainder = remainder.substring(1);
                    }
                    if (!remainder.isEmpty()) {
                        try {
                            remainder = URLDecoder.decode(remainder, StandardCharsets.UTF_8);
                            if (remainder.startsWith("http://") || remainder.startsWith("https://")) {
                                targetUrl = remainder;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Add CORS headers to all responses
            Headers respHeaders = exchange.getResponseHeaders();
            respHeaders.set("Access-Control-Allow-Origin", "*");
            respHeaders.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH");
            respHeaders.set("Access-Control-Allow-Headers", "*");
            respHeaders.set("Access-Control-Allow-Credentials", "true");
            respHeaders.set("Access-Control-Max-Age", "86400");

            // Handle pre-flight request locally
            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                log(method, proxyPath, "Preflight OPTIONS", 204, System.currentTimeMillis() - startTime);
                return;
            }

            // Health check fallback if no target URL is requested
            if (targetUrl == null || targetUrl.trim().isEmpty()) {
                byte[] response = "{\"status\":\"OK\",\"message\":\"CORS Proxy is running\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
                log(method, proxyPath, "Health check / Status page", 200, System.currentTimeMillis() - startTime);
                return;
            }

            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                byte[] errorMsg = "Invalid target URL protocol. Only HTTP/HTTPS supported.".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(400, errorMsg.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMsg);
                }
                log(method, proxyPath, "Bad Request: Invalid URL: " + targetUrl, 400, System.currentTimeMillis() - startTime);
                return;
            }

            try {
                String respBodyStr = "";
                String contentType = "text/html; charset=utf-8";
                int status = 200;

                String chromePath = findChromeExecutable();
                if (chromePath != null && requiresHeadlessBrowser(targetUrl)) {
                    try {
                        log("SYSTEM", proxyPath, "Launching Headless Chrome for JS execution: " + targetUrl, 200, 0);
                        respBodyStr = fetchHtmlWithHeadlessChrome(chromePath, targetUrl);
                        log("SYSTEM", proxyPath, "Headless Chrome fetch successful", 200, 0);
                    } catch (Exception ex) {
                        log("SYSTEM", proxyPath, "Chrome fetch failed: " + ex.getMessage() + ". Falling back to basic HTTP client.", 200, 0);
                        
                        // Fallback to basic HTTP request
                        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                                .uri(URI.create(targetUrl))
                                .timeout(Duration.ofSeconds(15));
                        
                        // Copy headers
                        Headers incomingHeaders = exchange.getRequestHeaders();
                        for (Map.Entry<String, List<String>> entry : incomingHeaders.entrySet()) {
                            String key = entry.getKey();
                            if (key != null && !RESTRICTED_HEADERS.contains(key.toLowerCase())) {
                                for (String val : entry.getValue()) {
                                    reqBuilder.header(key, val);
                                }
                            }
                        }

                        // Copy request body
                        byte[] bodyBytes = readInputStream(exchange.getRequestBody());
                        if (bodyBytes.length > 0) {
                            reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
                        } else {
                            if (method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("DELETE") || method.equalsIgnoreCase("HEAD")) {
                                reqBuilder.method(method, HttpRequest.BodyPublishers.noBody());
                            } else {
                                reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(new byte[0]));
                            }
                        }

                        HttpResponse<byte[]> targetResponse = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
                        status = targetResponse.statusCode();
                        respBodyStr = decodeResponseBody(targetResponse);
                        contentType = targetResponse.headers().firstValue("content-type").orElse("text/html; charset=utf-8");
                    }
                } else {
                    log("SYSTEM", proxyPath, "Google Chrome/Chromium not found. Falling back to basic HTTP client (No JS execution).", 200, 0);

                    // Standard HTTP request
                    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(targetUrl))
                            .timeout(Duration.ofSeconds(15));
                    
                    Headers incomingHeaders = exchange.getRequestHeaders();
                    for (Map.Entry<String, List<String>> entry : incomingHeaders.entrySet()) {
                        String key = entry.getKey();
                        if (key != null && !RESTRICTED_HEADERS.contains(key.toLowerCase())) {
                            for (String val : entry.getValue()) {
                                reqBuilder.header(key, val);
                            }
                        }
                    }

                    byte[] bodyBytes = readInputStream(exchange.getRequestBody());
                    if (bodyBytes.length > 0) {
                        reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
                    } else {
                        if (method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("DELETE") || method.equalsIgnoreCase("HEAD")) {
                            reqBuilder.method(method, HttpRequest.BodyPublishers.noBody());
                        } else {
                            reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(new byte[0]));
                        }
                    }

                    HttpResponse<byte[]> targetResponse = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
                    status = targetResponse.statusCode();
                    respBodyStr = decodeResponseBody(targetResponse);
                    contentType = targetResponse.headers().firstValue("content-type").orElse("text/html; charset=utf-8");
                }

                // Append a copy of the raw response to response.txt inside debug/ folder with separators for debugging
                try {
                    File servedFile = htmlFileSupplier.get();
                    File outputDir = null;
                    if (servedFile != null && servedFile.exists()) {
                        outputDir = servedFile.getParentFile();
                    } else {
                        File localFile = new File("app.html");
                        if (localFile.exists()) {
                            outputDir = localFile.getParentFile();
                        }
                    }
                    File debugDir = (outputDir != null) ? new File(outputDir, "debug") : new File("debug");
                    if (!debugDir.exists()) {
                        debugDir.mkdirs();
                    }
                    File destFile = new File(debugDir, "response.txt");
                    
                    String separatorBlock = String.format("\n===\nURL: %s\n===\n%s\n===\n", targetUrl, respBodyStr);
                    
                    synchronized (ProxyServer.class) {
                        java.nio.file.Files.writeString(
                                destFile.toPath(), 
                                separatorBlock, 
                                StandardCharsets.UTF_8,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND
                        );
                    }
                } catch (Exception ex) {
                    log("SYSTEM", proxyPath, "Warning: Failed to save response.txt: " + ex.getMessage(), 200, 0);
                }

                // Copy response headers if they contain content-type
                respHeaders.set("Content-Type", contentType);

                byte[] respBodyBytes = respBodyStr.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, respBodyBytes.length > 0 ? respBodyBytes.length : -1);
                
                if (respBodyBytes.length > 0) {
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBodyBytes);
                    }
                }

                long duration = System.currentTimeMillis() - startTime;
                log(method, proxyPath, targetUrl, status, duration);

            } catch (Exception e) {
                byte[] errorMsg = ("Proxy error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(502, errorMsg.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMsg);
                }
                log(method, proxyPath, targetUrl + " [Error: " + e.getMessage() + "]", 502, System.currentTimeMillis() - startTime);
            }
        }
    }

    private String findChromeExecutable() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String[] paths = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("USERPROFILE") + "\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe"
            };
            for (String path : paths) {
                if (new File(path).exists()) {
                    return path;
                }
            }
        } else if (os.contains("mac")) {
            String path = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
            if (new File(path).exists()) {
                return path;
            }
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            String[] paths = {
                "/usr/bin/google-chrome",
                "/usr/bin/chromium-browser",
                "/usr/bin/chromium",
                "/usr/local/bin/google-chrome",
                "/usr/local/bin/chromium"
            };
            for (String path : paths) {
                if (new File(path).exists()) {
                    return path;
                }
            }
            // Fallback: search on PATH using 'which'
            try {
                String[] commands = {"google-chrome", "chromium-browser", "chromium"};
                for (String cmd : commands) {
                    Process p = Runtime.getRuntime().exec(new String[]{"which", cmd});
                    if (p.waitFor() == 0) {
                        try (InputStream is = p.getInputStream()) {
                            String found = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                            if (!found.isEmpty()) {
                                return found;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean requiresHeadlessBrowser(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        // Skip API endpoints that don't render user-facing lists (prevents headless browser launch timeouts)
        if (lower.contains("getlist") || lower.contains("/preview/") || lower.contains("/rpc/") || lower.contains("generate_204")) {
            return false;
        }
        // Only trigger heavy headless browser for Google Maps list/playlist links
        return lower.contains("maps.app.goo.gl") || lower.contains("/maps/lists/") || lower.contains("/maps/playlist/");
    }

    private String fetchHtmlWithHeadlessChrome(String chromePath, String url) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                chromePath,
                "--headless",
                "--disable-gpu",
                "--dump-dom",
                "--no-sandbox",
                "--disable-extensions",
                "--disable-default-apps",
                "--no-default-browser-check",
                "--no-first-run",
                "--disable-features=Translate,SafeBrowsing",
                "--blink-settings=imagesEnabled=false",
                url
        );
        
        Process process = pb.start();
        
        // Read stdout asynchronously to avoid blocking issues
        byte[] outputBytes;
        try (InputStream is = process.getInputStream()) {
            outputBytes = is.readAllBytes();
        }
        
        // Wait up to 25 seconds for the JS rendering to complete
        boolean finished = process.waitFor(25, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Headless Chrome process timed out (25s limit).");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorMsg;
            try (InputStream es = process.getErrorStream()) {
                errorMsg = new String(es.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new IOException("Chrome exited with code " + exitCode + ". Error: " + errorMsg);
        }
        
        return new String(outputBytes, StandardCharsets.UTF_8);
    }

    private String getQueryParam(String query, String key) {
        if (query == null) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                if (k.equals(key)) {
                    return URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private byte[] readFileBytes(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return readInputStream(is);
        }
    }

    private byte[] readInputStream(InputStream is) throws IOException {
        return is.readAllBytes();
    }

    private String getContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private String getFallbackHtmlPage() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>CORS Proxy - Ready</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            background: #121214;\n" +
                "            color: #e1e1e6;\n" +
                "            font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            height: 100vh;\n" +
                "            margin: 0;\n" +
                "        }\n" +
                "        .card {\n" +
                "            background: #1d1d22;\n" +
                "            padding: 32px;\n" +
                "            border-radius: 16px;\n" +
                "            border: 1px solid #2e2e38;\n" +
                "            box-shadow: 0 10px 30px rgba(0,0,0,0.5);\n" +
                "            max-width: 480px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        h1 {\n" +
                "            color: #3ec3b0;\n" +
                "            margin-top: 0;\n" +
                "            font-size: 24px;\n" +
                "        }\n" +
                "        p {\n" +
                "            line-height: 1.6;\n" +
                "            color: #a9a9b2;\n" +
                "            margin-bottom: 24px;\n" +
                "        }\n" +
                "        code {\n" +
                "            background: #000;\n" +
                "            color: #ff79c6;\n" +
                "            padding: 2px 6px;\n" +
                "            border-radius: 4px;\n" +
                "            font-family: monospace;\n" +
                "        }\n" +
                "        .status-badge {\n" +
                "            display: inline-block;\n" +
                "            background: rgba(62, 195, 176, 0.15);\n" +
                "            color: #3ec3b0;\n" +
                "            padding: 6px 12px;\n" +
                "            border-radius: 20px;\n" +
                "            font-size: 13px;\n" +
                "            font-weight: bold;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <div class=\"status-badge\">Server running</div>\n" +
                "        <h1>No HTML File Selected</h1>\n" +
                "        <p>The CORS proxy server is running successfully, but it couldn't find an <code>app.html</code> file to serve.</p>\n" +
                "        <p>Please place your <code>app.html</code> in the same folder as the <code>cors_proxy.jar</code>, or drag & drop it directly into the Java GUI app window.</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String decodeResponseBody(HttpResponse<byte[]> response) throws IOException {
        byte[] bodyBytes = response.body();
        String encoding = response.headers().firstValue("content-encoding").orElse("").toLowerCase();
        if (encoding.contains("gzip")) {
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bodyBytes);
                 java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(bais)) {
                bodyBytes = gzis.readAllBytes();
            }
        } else if (encoding.contains("deflate")) {
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bodyBytes);
                 java.util.zip.InflaterInputStream iis = new java.util.zip.InflaterInputStream(bais)) {
                bodyBytes = iis.readAllBytes();
            }
        }
        return new String(bodyBytes, StandardCharsets.UTF_8);
    }
}
