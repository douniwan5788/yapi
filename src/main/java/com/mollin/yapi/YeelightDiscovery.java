package com.mollin.yapi;

import org.pmw.tinylog.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class YeelightDiscovery {
    private static final String ADVERTISEMENT_HOST = "239.255.255.250";
    private static final int ADVERTISEMENT_PORT = 1982;
    private static final String SEARCH_MESSAGE = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1982\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "ST: wifi_bulb";
    private static final int DEFAULT_SEARCH_TIME = 10000;

    public static void searchDevice(int timeout, TimeUnit unit, DiscoveryListener listener) {
        Set<URI> devices = new HashSet<>();
        boolean running = true;
        DatagramSocket socket;

        //join group
        if (timeout == 0) {

        } else
            //search once
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout((int) unit.toMillis(timeout));

                DatagramPacket data = new DatagramPacket(SEARCH_MESSAGE.getBytes(), SEARCH_MESSAGE.getBytes().length, InetAddress.getByName(ADVERTISEMENT_HOST), ADVERTISEMENT_PORT);

                socket.send(data);

                while (running) {
                    byte[] buffer = new byte[1024];
                    DatagramPacket dpRecv = new DatagramPacket(buffer, buffer.length);

                    try {
                        socket.receive(dpRecv);

                        if (!running)
                            break;

                        byte[] bytes = dpRecv.getData();
                        String message = new String(bytes);

                        Logger.error(message);

                        HashMap<String, String> info = new HashMap();
                        for (String line : message.split("\n")) {
                            String[] kvs = line.split(":", 2);
                            if (kvs.length != 2)
                                continue;
                            String key = kvs[0].trim().toLowerCase();
                            String value = kvs[1].trim().toLowerCase();
                            info.put(key, value);
                            switch (key) {
                                case "id":
                                    Logger.warn("id {}", value);
                                    break;
                                case "support":
                                    Logger.warn("support {}", value);
                                    break;
                                case "model":
                                    switch (value) {
                                        case "desklamp":
                                            info.put("max_ct", "6500");
                                            info.put("min_ct", "2700");
                                            break;
                                        default:
                                            info.put("max_ct", "6500");
                                            info.put("min_ct", "1700");
                                    }
                                default:
                                    break;
                            }
                        }
//                        Logger.info(info);
//                    Location: yeelight://192.168.7.211:55443
                        URI yeelight_uri = new URI(info.get("location"));

                        if (listener != null && !devices.contains(yeelight_uri))
                            listener.onDeviceFound(yeelight_uri, info);

                        devices.add(yeelight_uri);

                    } catch (SocketTimeoutException e) {
                        running = false;
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                }

                if (listener != null)
                    listener.onDiscoveryFinished(devices);

            } catch (IOException e) {
                e.printStackTrace();

                if (listener != null)
                    listener.onError(e);
            }
    }

    public interface DiscoveryListener {
        void onDeviceFound(URI device, HashMap info);

        void onDiscoveryFinished(Collection<URI> devices);

        void onError(Exception e);
    }
}