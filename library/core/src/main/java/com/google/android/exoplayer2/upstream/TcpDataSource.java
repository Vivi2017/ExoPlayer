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

import java.io.EOFException;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.android.exoplayer2.upstream.listen_server.SERVERPORT;
import static com.google.android.exoplayer2.upstream.listen_server.getInetAddress;
import static com.google.android.exoplayer2.upstream.listen_server.getLocalIpAddress;

/**
 * A UDP {@link DataSource}.
 */
public final class TcpDataSource implements DataSource {
    public String LOGTAG  = "vivitest";
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
    public static final int DEFAULT_READ_PACKET_SIZE = 1460;//1460
    public static final int CMD_LENGTH = 8;
    public static final int MAX_QUEUE_SIZE = 5;
    /**
     * The default socket timeout, in milliseconds.
     */
    public static final int DEAFULT_SOCKET_TIMEOUT_MILLIS = 8 * 1000;

    private final TransferListener<? super TcpDataSource> listener;
    private  byte[] packetBuffer;
    private  BlockingQueue<byte[]> packetBufferQue = new LinkedBlockingQueue<byte[]>();

    private byte[] localPacketBuffer;
    public long fileTotalLength = 0;
    public long fileRemainingLength = 0;
    public long fileStartPosition = 0;
    private byte[] cmdPacketBuffer;
    public long PacketBufferLength = 0;
    private Uri uri;
    private boolean opened;

    private long packetRemaining = 0;
    public Socket listensock;

    DataInputStream inCmdStream;
    DataInputStream  inFileStream;
    DataOutputStream outStream;

    ServerSocket serverSocket = null;
    Socket socketRec = null;
    Thread socketThead = null;

    public  final int  postvideo = 1001;
    public  final int  sendfile  = 1002;
    public  final int  getfile   = 1003;
    public  final int  byebyebye = 1004;
    public  final int  seekfile  = 1005;
    /*
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
        localPacketBuffer = new byte[maxPacketSize];
        cmdPacketBuffer= new byte[CMD_LENGTH];
        listensock = null;
        inCmdStream = null;
        inFileStream = null;
        outStream = null;
        serverSocket = null;
        socketRec = null;
        socketThead = null;
        Log.d(LOGTAG, " construct tcpdatasource" );

    }


    public void makeConnection() {
        if (serverSocket == null) {
            try {
                InetAddress tempInet = getInetAddress();
                String localIP = getLocalIpAddress();

                serverSocket = new ServerSocket(SERVERPORT, 0, tempInet);
                Log.d(LOGTAG, " vivi serverSocket ready " + localIP);

                if (socketRec == null) {
                    socketRec = serverSocket.accept();
                    if (socketRec != null) {
                        if (null == inCmdStream)
                            inCmdStream = new DataInputStream(socketRec.getInputStream());

                        while (inCmdStream != null && fileTotalLength == 0) {
                            // a channel is ready for reading
                            message_buffer_info messageCmd = new message_buffer_info(0, 0);
                            cmdPacketBuffer = new byte[CMD_LENGTH];
                            inCmdStream.readFully(cmdPacketBuffer, 0, CMD_LENGTH);
                            messageCmd = messageCmd.fromByteArray(cmdPacketBuffer);

                            if (messageCmd.type == postvideo) {
                                listensock = socketRec;
                                fileTotalLength = messageCmd.length;
                                fileRemainingLength = fileTotalLength;
                              //  if (listener != null) {
                              //      listener.onTransferStart(this, dataSpec);
                             //   }

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



    }

    @Override
    public long open(DataSpec dataSpec) throws TcpDataSourceException {
        uri = dataSpec.uri;
        Log.d(LOGTAG, " open tcpdatasource  dataSpec.length" + dataSpec.length +
        "  position " + dataSpec.position + "  filelength = " + fileTotalLength);

        if ((socketThead != null) && (socketThead.isInterrupted()==false))
        {
            socketThead.interrupt();
            socketThead = null;
        }

        makeConnection();

        //remote open file to read
        if(fileTotalLength > 0)
        {
            packetBufferQue.clear();
            fileStartPosition = dataSpec.position;

            fileRemainingLength  = (dataSpec.length == C.LENGTH_UNSET ? fileTotalLength - dataSpec.position
                    : dataSpec.length);
            if (fileRemainingLength < 0) {
                try {
                    throw new EOFException();
                } catch (EOFException e) {
                    e.printStackTrace();
                }
            }
            //remotefile.seek(dataSpec.position);
            packetRemaining = 0;
            PacketBufferLength = 0;

            seek(fileStartPosition);
        }

        if (socketThead == null) {
            socketThead = new Thread(new SocketHandleThread());
            socketThead.start();
        }

      //  if ((socketThead != null) && (socketThead.isInterrupted()==true))
      //  {
      //      socketThead.start();
      //  }

        opened = true;
         if (listener != null) {
              listener.onTransferStart(this, dataSpec);
           }


        if (fileRemainingLength <= 0)
            return C.LENGTH_UNSET;
        else
            return fileRemainingLength;
    }
    public void seek(long seekPosition)
    {
        if (outStream == null)
            try {
                outStream = new DataOutputStream(listensock.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }

        message_buffer_info messageCmd = new message_buffer_info(seekfile, (int)seekPosition);

        byte[] sendBuffer = messageCmd.toByteArray();
        try {
            outStream.write(sendBuffer, 0, CMD_LENGTH);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    @Override
    public int read(byte[] buffer, int offset, int readLength) throws TcpDataSourceException {
        if (readLength == 0) {
            return 0;
        }
        //Log.d(LOGTAG, "---- read lenght" + readLength + "  offset "+ offset + " packetRemaining =" + packetRemaining);
        if ((fileRemainingLength == 0 && fileTotalLength >0 ) && (packetRemaining == 0) &&
                (PacketBufferLength ==0) &&(packetBufferQue.size() == 0)) {
            return C.RESULT_END_OF_INPUT;
        }

        if ((packetRemaining <= 0 )&& (packetBufferQue.size() >0))
        {
            try {
                localPacketBuffer = packetBufferQue.take();
                PacketBufferLength = packetBufferQue.take().length;
                packetRemaining    = PacketBufferLength;

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        };

        if (packetRemaining > 0) {
            int packetOffset = (int)(PacketBufferLength - packetRemaining);
            int bytesToRead = (int)(Math.min(packetRemaining,readLength));
            System.arraycopy(localPacketBuffer,  packetOffset, buffer, offset,  bytesToRead);
            packetRemaining -= bytesToRead;

            if (listener != null) {
               // Log.d(LOGTAG, "---->local bytesToRead " + bytesToRead + " packetRemaining =" + packetRemaining);
                listener.onBytesTransferred(this,  bytesToRead);
            }
            return bytesToRead;

        }
        return 0;
    }


    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        Log.d(LOGTAG,"tcpdatasource close");
        packetBufferQue.clear();
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
            oos.writeLong(length);
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
            while (!Thread.currentThread().isInterrupted() && listensock!=null) {
                if ((packetBufferQue.size()>=MAX_QUEUE_SIZE)||
                        (listensock ==null)||
                        (fileRemainingLength <=0 && fileTotalLength>0))
                {
                    continue;
                }

                Log.d(LOGTAG, " ======request a package");
                if (outStream == null)
                    try {
                        outStream = new DataOutputStream(listensock.getOutputStream());
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }

                message_buffer_info messageCmd = new message_buffer_info(getfile, DEFAULT_READ_PACKET_SIZE);

                byte[] sendBuffer = messageCmd.toByteArray();
                try {
                    outStream.write(sendBuffer, 0, CMD_LENGTH);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

                message_buffer_info recvCmd = new message_buffer_info(0, 0);
                    cmdPacketBuffer = new byte[CMD_LENGTH];
                    try {
                        inCmdStream.readFully(cmdPacketBuffer, 0, CMD_LENGTH);
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                      recvCmd = recvCmd.fromByteArray(cmdPacketBuffer);
                     Log.d(LOGTAG, "=====read a remote package + "+ recvCmd.type + "  length =" + recvCmd.length);
                    if ((recvCmd.type == sendfile) && (recvCmd.length > 0)) {
                        if (inFileStream == null) {
                            try {
                                inFileStream = new DataInputStream(listensock.getInputStream());
                            } catch (IOException e1) {
                                e1.printStackTrace();
                                break;
                            }
                        }
                        if (inFileStream != null) {
                            try {
                                inFileStream.readFully(packetBuffer, 0, (int) recvCmd.length);
                                byte[] tempBuffer = new byte[(int) recvCmd.length];
                                System.arraycopy(packetBuffer, 0, tempBuffer, 0, (int) recvCmd.length);
                                packetBufferQue.put(tempBuffer);
                                fileRemainingLength -= recvCmd.length;
                                Log.d(LOGTAG, "======fileRemainingLength = " + fileRemainingLength +
                                        "\n packetBufferQue size = " + packetBufferQue.size());
                            } catch (IOException e1) {
                                e1.printStackTrace();
                                break;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                        continue;
                    }
                    if ((recvCmd.type == byebyebye) && (recvCmd.length == 0)) {
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
                }
            }
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
//System.arraycopy(tcpPacketBuffer, 0, packetBuffer, 0, messageCmd.length);

}
