package com.google.android.exoplayer2.upstream;

import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Created by vivisong on 10/05/2017.
 */
import java.net.DatagramSocket;


public class listen_server {

    public static int SERVERPORT = 12345;
    public static ServerSocket serverSocket;
    public static Thread serverThread = null;
    public static String LOGTAG = "Listen_activity";
    public static Socket socketRec = null;
    public static TcpDataSource listenDataSource = null;

    private static final listen_server instance = new listen_server( );

    public static listen_server getInstance(){
        return instance;
    }
    public listen_server()
    {
        socketRec = null;
        serverSocket = null;
        listenDataSource = null;
        serverThread = null;

    }

    public void listen_server_setDatasource(TcpDataSource tcpDataSource)
    {

        listenDataSource = tcpDataSource;


    }
    public static void start_listen_server() throws IOException {
        //stop_listen_server();
        if (serverThread == null) {
            serverThread =  new Thread(new SocketServerThread());
            serverThread.start();
        }

    }

    public static void onTcpDataSourceReady() throws SocketException {
       // if (listenDataSource != null)
       //     listenDataSource.onSocketReady(socketRec);

    }
    public void stop_listen_server() throws IOException {
        if(serverThread.isAlive()) {
            serverThread.interrupt();
        }
        try {
            serverThread.join(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new IOException(e);
        }

        serverSocket.close();
        socketRec = null;
        listenDataSource = null;
        serverThread = null;
    }

    public Socket getSocketRec() {
        return socketRec;
    }
    public static InetAddress getInetAddress()
    {
        try
        {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
            {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
                {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address)
                    {
                        return inetAddress;
                    }
                }
            }
        }
        catch (SocketException ex)
        {
            Log.e(LOGTAG, ex.toString());
        }
        return null;
    }
    public static String getLocalIpAddress()
    {
        try
        {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
            {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
                {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address)
                    {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        }
        catch (SocketException ex)
        {
            Log.e(LOGTAG, ex.toString());
        }
        return "";
    }

    static class SocketServerThread implements Runnable {

        public void run() {
            try {
                serverSocket = new ServerSocket(SERVERPORT);
                String localIP = getLocalIpAddress();

                Log.d(LOGTAG, " vivi serverSocket ready " + localIP);

            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if(socketRec == null) {
                        socketRec = serverSocket.accept();
                        Log.d(LOGTAG, "vivi serverSocket.accept()");
                        if (socketRec != null)
                            handle(socketRec);
                    }

                }  catch (SocketException ex)
                {
                    Log.e(LOGTAG, "err to accept connect ");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Respond to a request from a client.
         *
         * @param socket The client socket.
         * @throws IOException
         */
        private void handle(Socket socket) throws IOException {
            onTcpDataSourceReady();
        }

        /**
         * Writes a server error response (HTTP/1.0 500) to the given output stream.
         *
         * @param output The output stream.
         */
        private void writeServerError(PrintStream output) {
            output.println("HTTP/1.0 500 Internal Server Error");
            output.flush();
        }

        /**
         * Loads all the content of {@code fileName}.
         *
         * @param fileName The name of the file.
         * @return The content of the file.
         * @throws IOException
         */
        private byte[] loadContent(String fileName) throws IOException {
            InputStream input = null;
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                //input = mAssets.open(fileName);
                byte[] buffer = new byte[1024];
                int size;
                while (-1 != (size = input.read(buffer))) {
                    output.write(buffer, 0, size);
                }
                output.flush();
                return output.toByteArray();
            } catch (FileNotFoundException e) {
                return null;
            } finally {
                if (null != input) {
                    input.close();
                }
            }
        }

        /**
         * Detects the MIME type from the {@code fileName}.
         *
         * @param fileName The name of the file.
         * @return A MIME type.
         */
        private String detectMimeType(String fileName) {
            if (TextUtils.isEmpty(fileName)) {
                return null;
            } else if (fileName.endsWith(".html")) {
                return "text/html";
            } else if (fileName.endsWith(".js")) {
                return "application/javascript";
            } else if (fileName.endsWith(".css")) {
                return "text/css";
            } else {
                return "application/octet-stream";
            }
        }


    }
}
