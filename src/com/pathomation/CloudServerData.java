package com.pathomation;

public class CloudServerData {
    private boolean isSuccess;
    private String reason;
    private String serverUrl;
    private String sessionId;
    private String folder;
    private int responseSize;

    public CloudServerData(String serverUrl, String sessionId, String folder, int responseSize, boolean isSuccess, String reason) {
        this.serverUrl = serverUrl;
        this.sessionId = sessionId;
        this.folder = folder;
        this.responseSize = responseSize;
        this.isSuccess = isSuccess;
        this.reason = reason;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getFolder() {
        return folder;
    }
    public String getReason() {
        return reason;
    }

    public int getResponseSize() {
        return responseSize;
    }
    public boolean isSuccess() {
        return isSuccess;
    }
}
