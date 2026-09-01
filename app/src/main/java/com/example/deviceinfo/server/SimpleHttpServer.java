package com.example.deviceinfo.server;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Locale;

public final class SimpleHttpServer {

    public interface Handler {
        HttpResponse handle(String method, String path, String body);
    }

    private static final String TAG = "CafeHttpServer";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running;

    public void start(final int port, final Handler handler) {
        if (running) {
            return;
        }
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    running = true;
                    Log.i(TAG, "Listening on port " + port);
                    while (running) {
                        final Socket client = serverSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handleClient(client, handler);
                            }
                        }).start();
                    }
                } catch (IOException e) {
                    if (running) {
                        Log.e(TAG, "Server error", e);
                    }
                }
            }
        });
        serverThread.start();
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void handleClient(Socket client, Handler handler) {
        try {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            String requestLine = readAsciiLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return;
            }
            String method = parts[0];
            String path = parts[1];

            int contentLength = 0;
            String line;
            while ((line = readAsciiLine(in)) != null && !line.isEmpty()) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            String body = "";
            if (contentLength > 0) {
                byte[] bodyBytes = readFully(in, contentLength);
                body = new String(bodyBytes, UTF8);
            }

            HttpResponse response = handler.handle(method, path, body);
            writeResponse(out, response);
        } catch (Exception e) {
            Log.e(TAG, "Client error", e);
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String readAsciiLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (current == '\n') {
                break;
            }
            if (current != '\r') {
                buffer.write(current);
            }
            previous = current;
        }
        if (previous == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString("US-ASCII");
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int read = 0;
        while (read < length) {
            int chunk = in.read(data, read, length - read);
            if (chunk <= 0) {
                break;
            }
            read += chunk;
        }
        if (read < length) {
            byte[] trimmed = new byte[read];
            System.arraycopy(data, 0, trimmed, 0, read);
            return trimmed;
        }
        return data;
    }

    private static void writeResponse(OutputStream out, HttpResponse response) throws IOException {
        byte[] bodyBytes = response.body.getBytes(UTF8);
        String reason = response.statusCode == 200 ? "OK" : "Error";
        String headers = "HTTP/1.1 " + response.statusCode + " " + reason + "\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(headers.getBytes(UTF8));
        out.write(bodyBytes);
        out.flush();
    }
}
