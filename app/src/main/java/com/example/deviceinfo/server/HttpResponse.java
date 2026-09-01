package com.example.deviceinfo.server;

public final class HttpResponse {

    public final int statusCode;
    public final String body;

    public HttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public static HttpResponse ok(String json) {
        return new HttpResponse(200, json);
    }

    public static HttpResponse error(int code, String json) {
        return new HttpResponse(code, json);
    }
}
