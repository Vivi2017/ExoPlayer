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
    public static String LOGTAG = "Listen_activity";
    public static Socket socketRec = null;

    private static final listen_server instance = new listen_server( );

    public static listen_server getInstance(){
         return instance;
    }
    public listen_server()
    {
        if (serverSocket == null) {
            InetAddress tempInet = getInetAddress();
            String localIP = getLocalIpAddress();
            try {
                serverSocket = new ServerSocket(SERVERPORT, 0, tempInet);
                Log.d(LOGTAG, "serverSocket ready " + localIP);
                socketRec = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop_listen_server() throws IOException {
        serverSocket.close();
        serverSocket = null;
        socketRec = null;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }
    public void setClientSocket(Socket client) {

        socketRec = client;
    }
    public Socket getClientSocket() {
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


}
