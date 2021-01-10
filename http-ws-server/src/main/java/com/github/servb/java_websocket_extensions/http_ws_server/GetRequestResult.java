package com.github.servb.java_websocket_extensions.http_ws_server;

public class GetRequestResult {

    private final short statusCode;
    private final String statusText;
    private final String contentType;
    private final byte[] content;

    public GetRequestResult(final short statusCode, final String statusText, final String contentType, final byte[] content) {
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.contentType = contentType;
        this.content = content;
    }

    public short getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }
}
