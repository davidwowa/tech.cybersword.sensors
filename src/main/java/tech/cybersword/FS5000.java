package tech.cybersword;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fazecast.jSerialComm.SerialPort;

public class FS5000 {

    private static final Logger logger = LogManager.getLogger(FS5000.class);

    public static void main(String[] args) {
        String[] portNames = { "/dev/tty.usbserial-1440", "/dev/cu.usbserial-1440" };
        SerialPort serialPort = null;

        for (String portName : portNames) {
            serialPort = SerialPort.getCommPort(portName);
            if (serialPort.openPort()) {
                logger.info("Connected to port: " + portName);
                break;
            }
        }

        if (serialPort == null || !serialPort.isOpen()) {
            logger.error("Failed to open any specified ports.");
            return;
        }

        serialPort.setComPortParameters(115200, 9, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 0);

        try {
            byte[] messageBytes = { (byte) 0xAA, 0x05, 0x0E, 0x01, (byte) 0xBE, 0x55 };

            byte[] unsignedBytes = new byte[messageBytes.length];
            for (int i = 0; i < messageBytes.length; i++) {
                unsignedBytes[i] = (byte) Byte.toUnsignedInt(messageBytes[i]);
            }
            serialPort.writeBytes(unsignedBytes, unsignedBytes.length);

            logger.info("Sent to port: " + Arrays.toString(unsignedBytes));

            int counter = 0;

            while (counter < 1000) {

                Thread.sleep(1000);

                int aBytes = serialPort.bytesAvailable();

                byte[] readBuffer = new byte[aBytes];

                int numRead = serialPort.readBytes(readBuffer, aBytes);
                if (0 != numRead) {
                    String response = new String(readBuffer, 0, aBytes);

                    response = "FS5000:" + response;
                    // send(response); // TODO ...
                    broadcast(response, InetAddress.getByName("255.255.255.255"), 44444);

                    logger.info("Received from serial port: " + response);
                } else {
                    counter++;
                    logger.warn("counter to exit: " + counter);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        } finally {
            serialPort.closePort();
            logger.info("Port closed.");
        }
    }

    public static void broadcast(String broadcastMessage, InetAddress address, int port) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);

        byte[] buffer = broadcastMessage.getBytes();

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
        socket.close();

        logger.info("Broadcast message sent: " + broadcastMessage);
    }

    private static void send(String data) throws Exception {

        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        String urlString = "https://server:1880/radiation?data=" + URLEncoder.encode(data, "UTF-8");
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        logger.info("GET Response Code :: " + responseCode);
    }
}
