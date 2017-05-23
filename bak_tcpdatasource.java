package com.google.android.exoplayer2.upstream;

/**
 * Created by vivisong on 10/05/2017.
 */

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.R.attr.key;
import static android.R.attr.offset;
import static android.R.attr.start;
import static android.net.Uri.EMPTY;
import static com.google.android.exoplayer2.upstream.listen_server.SERVERPORT;
import static com.google.android.exoplayer2.upstream.listen_server.getLocalIpAddress;
import static com.google.android.exoplayer2.upstream.listen_server.serverSocket;
import static com.google.android.exoplayer2.upstream.listen_server.serverThread;
import static com.google.android.exoplayer2.upstream.listen_server.socketRec;
import static com.google.android.exoplayer2.upstream.listen_server.start_listen_server;

/**
 * A UDP {@link DataSource}.
 */
public final class TcpDataSource implements DataSource {
    public String LOGTAG  = "Listen_activity";
    /**
     * The default connection timeout, in milliseconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
    /**
     * The default read timeout, in milliseconds.
     */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;
    /**
     * Thrown when an error is encountered when trying to read from a {@link com.google.android.exoplayer2.upstream.UdpDataSource}.
     */
    public static final class TcpDataSourceException extends IOException {

        public TcpDataSourceException(IOException cause) {
            super(cause);
        }

    }

    /**
     * The default maximum datagram packet size, in bytes.
     */
    public static final int DEFAULT_MAX_PACKET_SIZE = 2000;
    public static final int DEFAULT_READ_PACKET_SIZE = 1024;
    public static final int CMD_LENGTH = 8;

    /**
     * The default socket timeout, in milliseconds.
     */
    public static final int DEAFULT_SOCKET_TIMEOUT_MILLIS = 8 * 1000;

    private final TransferListener<? super TcpDataSource> listener;
    private final byte[] packetBuffer;
    private final byte[] tcpPacketBuffer;
    private final byte[] tcpPacketBufferFirst;
    public  int  tcpPacketBufferLength = 0;
    public  int  fileTotalLength = 0;
    public  int  fileRemainingLength = 52*1024;
    private byte[] cmdPacketBuffer;
    //private final DatagramPacket packet;
    int readCount = 0;
    int requestCount = 0;
    public  int requestLength = 0;
    private Uri uri;
    //private DatagramSocket socket;
    private MulticastSocket multicastSocket;
    private InetAddress address;
    private InetSocketAddress socketAddress;
    private boolean opened;


    private int packetRemaining = 0;
    public Socket listensock;

    DataInputStream inCmdStream;
    DataInputStream  inFileStream;
    DataOutputStream outStream;

    ServerSocket serverSocket = null;
    Socket socketRec = null;
    Thread socketThead = null;
    Thread socketRequestThead = null;
    public  int  postvideo = 1;
    public  int  sendfile  = 2;//2
    public  int  getfile   = 3;//3
    public  int  byebye    = 4;
    public boolean readQuest = false;


    final Lock lock = new ReentrantLock();
    final Condition requestRemote  = lock.newCondition();
    final Condition readRemote  = lock.newCondition();
    final Condition readLocal = lock.newCondition();
    final Condition submitLocal = lock.newCondition();
    /**
     * @param listener An optional listener.
     */
    public TcpDataSource(TransferListener<? super TcpDataSource> listener) {
        this(listener, DEFAULT_MAX_PACKET_SIZE);
    }

    /**
     * @param listener An optional listener.
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     */
    public TcpDataSource(TransferListener<? super TcpDataSource> listener, int maxPacketSize) {
        this(listener, maxPacketSize, DEAFULT_SOCKET_TIMEOUT_MILLIS);
    }

    /**
     * @param listener An optional listener.
     * @param maxPacketSize The maximum datagram packet size, in bytes.
     * @param socketTimeoutMillis The socket timeout in milliseconds. A timeout of zero is interpreted
     *     as an infinite timeout.
     */
    public TcpDataSource(TransferListener<? super TcpDataSource> listener, int maxPacketSize,
                         int socketTimeoutMillis) {
        this.listener = listener;
        packetBuffer = new byte[maxPacketSize];
        tcpPacketBuffer= new byte[maxPacketSize];
        tcpPacketBufferFirst= new byte[maxPacketSize];
        cmdPacketBuffer= new byte[CMD_LENGTH];
        readCount = 0;
        requestCount = 0;
        listensock = null;
        inCmdStream = null;
        inFileStream = null;
        outStream = null;
        serverSocket = null;
        socketRec = null;
        socketThead = null;
        socketRequestThead = null;
    }


    public long onSocketReady(Socket socketRec)  {

        return fileTotalLength;
    }
    public void makeConnection() {

    }

    @Override
    public long open(DataSpec dataSpec) throws TcpDataSourceException {
        uri = dataSpec.uri;

        if (serverSocket == null) {
            try {
                serverSocket = new ServerSocket(SERVERPORT);
                String localIP = getLocalIpAddress();

                Log.d(LOGTAG, " vivi serverSocket ready " + localIP);

            } catch (IOException e){
                e.printStackTrace();
            }
        }
        if (serverSocket != null){
            try {
                if (socketRec == null) {
                    socketRec = serverSocket.accept();
                    if (socketRec != null) {
                        if (null == inCmdStream) try {
                            inCmdStream = new DataInputStream(socketRec.getInputStream());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        while (inCmdStream != null && fileTotalLength==0) {
                            // a channel is ready for reading
                            message_buffer_info messageCmd = new message_buffer_info(0, 0);
                            cmdPacketBuffer = new byte[CMD_LENGTH];
                            try {
                                inCmdStream.readFully(cmdPacketBuffer, 0, CMD_LENGTH);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            messageCmd = messageCmd.fromByteArray(cmdPacketBuffer);

                            if (messageCmd.type == postvideo) {
                                listensock = socketRec;
                                fileTotalLength = messageCmd.length;
                                fileRemainingLength = fileTotalLength;
                                if (listener != null) {
                                    listener.onTransferStart(this, dataSpec);
                                }
                                opened = true;

                                if(socketRequestThead == null ){
                                    socketRequestThead = new Thread(new SocketRequestThread());
                                    socketRequestThead.start();
                                }
                                if(socketThead== null ){
                                    socketThead = new Thread(new SocketHandleThread());
                                    socketThead.start();
                                }



                                break;
                            }

                        }
                    }
                }

            } catch (SocketException ex) {
                Log.e(LOGTAG, "err to accept connect ");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (fileTotalLength == 0)
            return C.LENGTH_UNSET;
        else
            return fileTotalLength;
    }


    @Override
    public int read(byte[] buffer, int offset, int readLength) throws TcpDataSourceException {
        if (readLength == 0) {
            return 0;
        }

        if ((fileRemainingLength == 0) &&(packetRemaining == 0)) {
             return C.RESULT_END_OF_INPUT;
          }
        requestLength = readLength;
        readQuest = true;

        {
            lock.lock();
            try {
                if (packetRemaining == 0)
                    try {
                        Log.d(LOGTAG, "wait for submitLocal");
                        requestRemote.signal();
                        submitLocal.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                int packetOffset = tcpPacketBufferLength - packetRemaining;
                int bytesToRead = Math.min(packetRemaining, readLength);
                System.arraycopy(packetBuffer, packetOffset, buffer, offset, bytesToRead);
                packetRemaining -= bytesToRead;
                if (packetRemaining == 0) {
                    requestCount = 0;
                    Log.d(LOGTAG, " signal readRemote");
                    requestRemote.signal();
                }
                if (listener != null) {
                    Log.d(LOGTAG, "---->local bytesToRead " + bytesToRead + " packetRemaining =" + packetRemaining);
                    listener.onBytesTransferred(this, bytesToRead);
                }
                return bytesToRead;
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {

        uri = null;
        if(socketThead.isAlive() == true)
        {
            socketThead.interrupt();
            socketThead = null;
        }
        if(socketRequestThead.isAlive() == true)
        {
            socketRequestThead.interrupt();
            socketRequestThead = null;
        }

        if (listensock != null) {
            try {
                inFileStream.close();
                inCmdStream.close();
                listensock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            listensock = null;
        }
        socketRec = null;
        packetRemaining = 0;
        if (opened) {
            opened = false;
            if (listener != null) {
                listener.onTransferEnd(this);
            }
        }
        try {
            serverSocket.close();
            serverSocket = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    class message_buffer_info implements java.io.Serializable {
        public int type;//1 post mp4, 2 send mp4 file , 3 remote get file,;
        public int length;
        message_buffer_info(int t, int l)
        {
            type = t;
            length = l;

        }
        public byte [] toByteArray() {

            ByteBuffer b = ByteBuffer.allocate(CMD_LENGTH);

            return b.putInt(type).putInt(length).array();
        }

        public  message_buffer_info fromByteArray(byte [] bytes) {
            if (bytes.length < CMD_LENGTH) throw new IllegalArgumentException("not enough bytes");

            ByteBuffer b = ByteBuffer.wrap(bytes);
            int type = b.getInt();
            int length = b.getInt();
            return new message_buffer_info(type, length);
        }

        public  byte[] getBytes() throws java.io.IOException{
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeInt(type);
            oos.writeInt(length);
            //oos.writeObject(this);
            oos.flush();
            oos.close();
            bos.close();
            byte [] data = bos.toByteArray();
            return data;
        }
    }


    private class SocketHandleThread implements Runnable {

        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                lock.lock();
                try {
                    if (requestCount == 0) try {
                        Log.d(LOGTAG, "wait for readRemote");
                        readRemote.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Log.d(LOGTAG, "try to read a remote package");
                    message_buffer_info messageCmd = new message_buffer_info(0, 0);
                    cmdPacketBuffer = new byte[CMD_LENGTH];
                    try {
                        inCmdStream.readFully(cmdPacketBuffer, 0, CMD_LENGTH);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if ((messageCmd.type == sendfile) && (messageCmd.length > 0)) {
                        if (inFileStream == null) {
                            try {
                                inFileStream = new DataInputStream(listensock.getInputStream());
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                        if (inFileStream != null) {
                            try {
                                inFileStream.readFully(tcpPacketBuffer, 0, messageCmd.length);
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            // big endian
                            // ByteBuffer byteBuffer = ByteBuffer.wrap(tcpPacketBuffer);
                            //  byteBuffer.order( ByteOrder.LITTLE_ENDIAN);

                            //  ByteBuffer destByteBuffer = ByteBuffer.allocate(messageCmd.length);
                            //  destByteBuffer.order(ByteOrder.BIG_ENDIAN);
                            //  IntBuffer destBuffer = destByteBuffer.asIntBuffer();

                            //    while (byteBuffer.hasRemaining())
                            //  {
                            //      int element = byteBuffer.getInt();
                            //     destBuffer.put(element);
                            // }
                            System.arraycopy(tcpPacketBuffer, 0, packetBuffer, 0, messageCmd.length);

                            readCount++;
                            requestCount = 0;
                            packetRemaining += messageCmd.length;
                            tcpPacketBufferLength = messageCmd.length;
                           // if (fileRemainingLength == fileTotalLength)//bak first package
                           // {
                           //     System.arraycopy(tcpPacketBuffer, 0, tcpPacketBufferFirst, 0, messageCmd.length);
                                //firstPacket = true;
                           // }
                            fileRemainingLength -= messageCmd.length;
                            Log.d(LOGTAG, "---->get read count = " + readCount + " packetRemaining =" + packetRemaining);
                            Log.d(LOGTAG, "fileRemainingLength = " + fileRemainingLength);
                            Log.d(LOGTAG, "signal submitLocal");
                            submitLocal.signal();
                        }
                        continue;
                    }
                    if ((messageCmd.type == byebye) && (messageCmd.length == 0)) {
                        Log.d(LOGTAG, "remote send byebye");
                        if (inCmdStream != null)
                            try {
                                inCmdStream.close();
                                inCmdStream = null;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        if (inFileStream != null)
                            try {
                                inFileStream.close();
                                inFileStream = null;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        if (listensock != null)
                            try {
                                listensock.getOutputStream().close();
                                listensock.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }


    private class SocketRequestThread implements Runnable {

        public void run() {
            while (!Thread.currentThread().isInterrupted()){
                if(readQuest == false){
                    continue;
                }
                lock.lock();
                try {
                    if (packetRemaining > 0)
                        try {
                            requestRemote.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    if (packetRemaining == 0 && listensock != null && (requestLength != 0) && opened == true) {
                        // send read to remote;
                        if (outStream == null)
                            try {
                                outStream = new DataOutputStream(listensock.getOutputStream());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        message_buffer_info messageCmd = new message_buffer_info(getfile, requestLength);

                        byte[] sendBuffer = messageCmd.toByteArray();
                        try {
                            outStream.write(sendBuffer, 0, CMD_LENGTH);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try {
                            outStream.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        requestCount = 1;
                        //readfrom remote
                        Log.d(LOGTAG, "signal to read a remote package");
                        readRemote.signal();
                    }
                }finally {
                    lock.unlock();
                }
            }
        }
    }
}
