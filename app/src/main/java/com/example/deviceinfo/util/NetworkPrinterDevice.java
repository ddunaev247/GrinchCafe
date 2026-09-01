package com.example.deviceinfo.util;

public class NetworkPrinterDevice {

    private final String host;
    private final int port;
    private final String label;

    public NetworkPrinterDevice(String host, int port, String label) {
        this.host = host;
        this.port = port;
        this.label = label;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayAddress() {
        return host + ":" + port;
    }
}
