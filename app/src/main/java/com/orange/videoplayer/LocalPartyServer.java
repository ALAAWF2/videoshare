package com.orange.videoplayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

/**
 * Tiny pure-Java HTTP server (no dependencies) that serves the Watch Party
 * player page over the local network, so iPhone/other devices can open a
 * regular http:// link in Safari with FULL sync — no file needed.
 */
public class LocalPartyServer {

    private static volatile ServerSocket serverSocket;
    private static volatile int port = -1;
    private static volatile String currentRoom;
    private static volatile String currentUrl;
    private static volatile String currentTitle;

    /** Starts serving the current party page. Returns the LAN URL or null on failure. */
    public static String start(String roomId, String videoUrl, String title) {
        stop();
        currentRoom = roomId;
        currentUrl = videoUrl;
        currentTitle = title;
        try {
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            Thread t = new Thread(LocalPartyServer::acceptLoop, "MyPlyr-LocalPartyServer");
            t.setDaemon(true);
            t.start();
            return getLanUrl();
        } catch (IOException e) {
            port = -1;
            serverSocket = null;
            return null;
        }
    }

    public static void stop() {
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
            port = -1;
        }
    }

    public static boolean isRunning() {
        return serverSocket != null && !serverSocket.isClosed();
    }

    /** e.g. http://192.168.1.5:43217/ — null if not running or no IPv4 found. */
    public static String getLanUrl() {
        if (!isRunning()) return null;
        String ip = getLocalIpv4();
        if (ip == null) return null;
        return "http://" + ip + ":" + port + "/";
    }

    private static void acceptLoop() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket s = serverSocket.accept();
                handleClient(s);
            } catch (IOException ignored) {
            }
        }
    }

    private static void handleClient(Socket s) {
        try {
            s.setSoTimeout(4000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String requestLine = in.readLine(); // e.g. GET / HTTP/1.1
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // drain headers
            }

            byte[] body;
            if (requestLine != null && requestLine.startsWith("GET ")) {
                body = WatchPartyWebPlayer.getSelfContainedHtml(currentRoom, currentUrl, currentTitle)
                        .getBytes("UTF-8");
            } else {
                body = "OK".getBytes("UTF-8");
            }

            OutputStream out = s.getOutputStream();
            String head = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: " + body.length + "\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(head.getBytes("UTF-8"));
            out.write(body);
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private static String getLocalIpv4() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress ia = addrs.nextElement();
                    if (ia instanceof Inet4Address && !ia.isLoopbackAddress()) {
                        return ia.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
