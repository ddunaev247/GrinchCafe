package com.example.deviceinfo.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class NetworkPrinterScanner {

    public interface ScanCallback {
        void onProgress(int scanned, int total);
        void onComplete(List<NetworkPrinterDevice> devices);
        void onError(String message);
    }

    private static final int[] PRINTER_PORTS = {9100, 9101, 9102, 515};
    private static final int CONNECT_TIMEOUT_MS = 400;
    private static final int THREADS = 32;

    private NetworkPrinterScanner() {
    }

    public static void scanAsync(final Context context, final ScanCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String localIp = getLocalIpAddress(context);
                    if (localIp == null || localIp.isEmpty()) {
                        callback.onError("Не удалось определить IP устройства. Подключитесь к Wi-Fi.");
                        return;
                    }

                    String subnet = getSubnetPrefix(localIp);
                    if (subnet == null) {
                        callback.onError("Некорректный IP: " + localIp);
                        return;
                    }

                    final int total = 254;
                    final AtomicInteger scanned = new AtomicInteger(0);
                    final CopyOnWriteArrayList<NetworkPrinterDevice> found =
                            new CopyOnWriteArrayList<>();

                    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
                    for (int i = 1; i <= 254; i++) {
                        final String host = subnet + i;
                        if (host.equals(localIp)) {
                            scanned.incrementAndGet();
                            continue;
                        }
                        pool.execute(new Runnable() {
                            @Override
                            public void run() {
                                probeHost(host, found);
                                int done = scanned.incrementAndGet();
                                callback.onProgress(done, total);
                            }
                        });
                    }

                    pool.shutdown();
                    pool.awaitTermination(60, TimeUnit.SECONDS);

                    List<NetworkPrinterDevice> result = new ArrayList<>(found);
                    Collections.sort(result, new java.util.Comparator<NetworkPrinterDevice>() {
                        @Override
                        public int compare(NetworkPrinterDevice a, NetworkPrinterDevice b) {
                            return a.getHost().compareTo(b.getHost());
                        }
                    });
                    callback.onComplete(result);
                } catch (Exception e) {
                    callback.onError(e.getMessage() == null ? "Ошибка сканирования" : e.getMessage());
                }
            }
        }).start();
    }

    private static void probeHost(String host, CopyOnWriteArrayList<NetworkPrinterDevice> found) {
        for (int port : PRINTER_PORTS) {
            if (isPortOpen(host, port)) {
                String label = "ESC/POS · порт " + port;
                found.add(new NetworkPrinterDevice(host, port, label));
                break;
            }
        }
    }

    private static boolean isPortOpen(String host, int port) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String getLocalIpAddress(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null && info.isConnected()) {
                    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                        WifiManager wm = (WifiManager)
                                context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        if (wm != null) {
                            WifiInfo wifiInfo = wm.getConnectionInfo();
                            int ip = wifiInfo.getIpAddress();
                            if (ip != 0) {
                                return formatIp(ip);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String formatIp(int ip) {
        return String.format(Locale.US, "%d.%d.%d.%d",
                (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
    }

    private static String getSubnetPrefix(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        return parts[0] + "." + parts[1] + "." + parts[2] + ".";
    }
}
