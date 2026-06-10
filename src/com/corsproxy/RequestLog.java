package com.corsproxy;

public class RequestLog {
    private final String timestamp;
    private final String method;
    private final String path;
    private final String targetUrl;
    private final int status;
    private final long durationMs;

    public RequestLog(String timestamp, String method, String path, String targetUrl, int status, long durationMs) {
        this.timestamp = timestamp;
        this.method = method;
        this.path = path;
        this.targetUrl = targetUrl != null ? targetUrl : "-";
        this.status = status;
        this.durationMs = durationMs;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public int getStatus() {
        return status;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
