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
package com.google.android.exoplayer2.upstream;

import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.R.attr.offset;
import static com.google.android.exoplayer2.upstream.TcpDataSource.CMD_LENGTH;
import static com.google.android.exoplayer2.upstream.TcpDataSource.MAX_QUEUE_SIZE;
import static com.google.android.exoplayer2.upstream.listen_server.LOGTAG;
import static com.google.android.exoplayer2.upstream.listen_server.SERVERPORT;
import static com.google.android.exoplayer2.upstream.listen_server.getInetAddress;
import static com.google.android.exoplayer2.upstream.listen_server.getLocalIpAddress;
import static com.google.android.exoplayer2.upstream.listen_server.serverSocket;
import static com.google.android.exoplayer2.upstream.listen_server.socketRec;

/**
 * A {@link DataSource} for reading local files.
 */
public final class FileDataSource implements DataSource {

    /**
     * Thrown when IOException is encountered during local file read operation.
     */
    public static class FileDataSourceException extends IOException {

        public FileDataSourceException(IOException cause) {
            super(cause);
        }

    }

    private final TransferListener<? super FileDataSource> listener;

    private RandomAccessFile file;
    private Uri uri;
    private long bytesRemaining = C.LENGTH_UNSET;
    private long localRemaining = C.LENGTH_UNSET;
    private long sendLocalAll = C.LENGTH_UNSET;
    private byte[] packetBuffer;
    private byte[] localPacketBuffer;
    private BlockingQueue<byte[]> packetBufferQue = new LinkedBlockingQueue<byte[]>();

    public long fileTotalLength = C.LENGTH_UNSET;
    public int fileOffset = 0;
    private byte[] cmdPacketBuffer;
    public long PacketBufferLength = C.LENGTH_UNSET;

    private boolean opened;

    private boolean remoteFile = false;
    private long packetRemaining = C.LENGTH_UNSET;
    public Socket listensock = null;

    private DataInputStream inCmdStream = null;
    private DataInputStream inFileStream = null;
    private DataOutputStream outStream = null;

    private ServerSocket serverSocket = null;
    private Socket socketRec = null;
    private Thread socketThead = null;

    public final int postvideo = 1001;
    public final int sendfile = 1002;
    public final int getfile = 1003;
    public final int byebyebye = 1004;
    public final int seekfile = 1005;
    public static final int DEFAULT_MAX_PACKET_SIZE = 2000;
    public static final int DEFAULT_READ_PACKET_SIZE = 1460;//1460
    public static final int CMD_LENGTH = 12;
    public static final int MAX_QUEUE_SIZE = 1;
    public int testTHread = 0;

    public String LOGTAG = "vivitest";

    public FileDataSource() {
        this(null);
    }

    /**
     * @param listener An optional listener.
     */
    public FileDataSource(TransferListener<? super FileDataSource> listener) {
        this.listener = listener;
        packetBuffer = new byte[2000];
        //localPacketBuffer = new byte[maxPacketSize];
        cmdPacketBuffer = new byte[CMD_LENGTH];
    }

    @Override
    public long open(DataSpec dataSpec) throws FileDataSourceException {
        if (dataSpec.uri.getPath().startsWith("/sdcard/Movies/fake")) {
            Log.d(LOGTAG, " open tcpdatasource  dataSpec.length" + dataSpec.length +
                    "  position " + dataSpec.position + "  filelength = " + fileTotalLength);
            uri = dataSpec.uri;
            remoteFile = true;
            localRemaining = C.LENGTH_UNSET;
            bytesRemaining = C.LENGTH_UNSET;
            /* test one thread
            if ((socketThead != null) && (socketThead.isInterrupted() == false)) {
                socketThead.interrupt();
                socketThead = null;
            }
*/
            makeConnection();

            //remote open file to read
            if (fileTotalLength > 0) {
                packetRemaining = 0;
                PacketBufferLength = 0;
                sendLocalAll = 0;
                packetBufferQue.clear();
                fileOffset = (int) dataSpec.position;
                localRemaining = 0;
                bytesRemaining = localRemaining = (dataSpec.length == C.LENGTH_UNSET ? fileTotalLength - dataSpec.position
                        : dataSpec.length);

                if (localRemaining < 0) {
                    try {
                        throw new EOFException();
                    } catch (EOFException e) {
                        e.printStackTrace();
                    }
                }
                //remotefile.seek(dataSpec.position);
                seek(dataSpec.position);
            }
/* test one thread
            if (socketThead == null) {
                socketThead = new Thread(new SocketHandleThread());
                socketThead.start();
            }
*/
        } else {
            remoteFile = false;
            try {
                uri = dataSpec.uri;
                file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
                file.seek(dataSpec.position);
                localRemaining = dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position
                        : dataSpec.length;

                bytesRemaining = localRemaining;
                if (localRemaining < 0) {
                    throw new EOFException();
                }
            } catch (IOException e) {
                throw new FileDataSourceException(e);
            }
        }
        opened = true;
        if (listener != null) {
            listener.onTransferStart(this, dataSpec);
        }

        return localRemaining;
    }
    public int readRemote(byte[] buffer, int offset, int readLength)
    {
        int bytesRead = 0;
        if (listensock == null){
            return bytesRead;
        }
        if (localRemaining == sendLocalAll && localRemaining > 0) {
            return C.RESULT_END_OF_INPUT;
        }

        if (packetRemaining > 0) {
            int packetOffset = (int) (PacketBufferLength - packetRemaining);
            bytesRead = (int) (Math.min(packetRemaining, readLength));
            System.arraycopy(packetBuffer, packetOffset, buffer, offset, bytesRead);
            packetRemaining -= bytesRead;
            sendLocalAll += bytesRead;
            return bytesRead;
        }

        Log.d(LOGTAG, " ======local out, request a package from remote");
        message_buffer_info messageCmdSend = new message_buffer_info(getfile, fileOffset, DEFAULT_READ_PACKET_SIZE);
        byte[] sendBuffer = messageCmdSend.toByteArray();

        if (outStream == null)
            try {
                outStream = new DataOutputStream(listensock.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }

        try {
            outStream.write(sendBuffer, 0, CMD_LENGTH);
        } catch (IOException e) {
            e.printStackTrace();
        }

        message_buffer_info recvCmd = new message_buffer_info(0, 0, 0);
        cmdPacketBuffer = new byte[CMD_LENGTH];
        if (inCmdStream == null)
            try {
                inCmdStream = new DataInputStream(listensock.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        try {
            inCmdStream.readFully(cmdPacketBuffer, 0, CMD_LENGTH);
        } catch (IOException e) {
            e.printStackTrace();

        }
        recvCmd = recvCmd.fromByteArray(cmdPacketBuffer);

        if ((recvCmd.type == sendfile) && (recvCmd.length > 0)) {
            if (inFileStream == null) {
                try {
                    inFileStream = new DataInputStream(listensock.getInputStream());
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (inFileStream != null) {
                try {
                    inFileStream.readFully(packetBuffer, 0, recvCmd.length);
                    if (recvCmd.offset != (int) fileOffset) {
                        Log.d(LOGTAG, "drop for offset = " + recvCmd.offset);
                    }
                    Log.d(LOGTAG, "remote package  arrive" +
                            " length =" + recvCmd.length +
                            " offset = " + recvCmd.offset);

                    PacketBufferLength = recvCmd.length;
                    bytesRemaining -= recvCmd.length;
                    packetRemaining = PacketBufferLength;

                    Log.d(LOGTAG, "======remote bytesRemaining = " + bytesRemaining);

                    if (packetRemaining > 0) {
                        int packetOffset = (int) (PacketBufferLength - packetRemaining);
                        bytesRead = (int) (Math.min(packetRemaining, readLength));
                        System.arraycopy(packetBuffer, packetOffset, buffer, offset, bytesRead);
                        packetRemaining -= bytesRead;
                        sendLocalAll += bytesRead;
                        return bytesRead;
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        } else if ((recvCmd.type == byebyebye) && (recvCmd.length == 0)) {
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

        return bytesRead;
    }
    @Override
    public int read(byte[] buffer, int offset, int readLength) throws FileDataSourceException {
        if (readLength == 0) {
            return 0;
        } else {
            int bytesRead = 0;
            if (uri.getPath().startsWith("/sdcard/Movies/fake")) {
                bytesRead = readRemote(buffer, offset, readLength);
                if (listener != null) {
                    listener.onBytesTransferred(this, bytesRead);
                }
                return bytesRead;

            } else if (testTHread == 1) {
            if (localRemaining == sendLocalAll && localRemaining > 0) {
                return C.RESULT_END_OF_INPUT;
            }

            if ((packetRemaining <= 0) && (packetBufferQue.size() > 0)) {
                try {
                    localPacketBuffer = packetBufferQue.take();
                    PacketBufferLength = packetBufferQue.take().length;
                    packetRemaining = PacketBufferLength;

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            if (packetRemaining > 0) {
                int packetOffset = (int) (PacketBufferLength - packetRemaining);
                bytesRead = (int) (Math.min(packetRemaining, readLength));
                System.arraycopy(localPacketBuffer, packetOffset, buffer, offset, bytesRead);
                packetRemaining -= bytesRead;
                sendLocalAll += bytesRead;
            }


            if (listener != null) {
                listener.onBytesTransferred(this, bytesRead);
            }
            return bytesRead;
        } else {
            if (bytesRemaining == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            try {
                bytesRead = file.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
            } catch (IOException e) {
                throw new FileDataSourceException(e);
            }
            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
            }


            if (listener != null) {
                listener.onBytesTransferred(this, bytesRead);
            }
            return bytesRead;
        }
    }
    }

  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws FileDataSourceException {
    uri = null;
    try {
      if (file != null) {
        file.close();
      }
    } catch (IOException e) {
      throw new FileDataSourceException(e);
    } finally {
      file = null;
      if (opened) {
        opened = false;
        if (listener != null) {
          listener.onTransferEnd(this);
        }
      }
    }
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

            while (inCmdStream != null && fileTotalLength == C.LENGTH_UNSET) {
              // a channel is ready for reading
              message_buffer_info messageCmd = new message_buffer_info(0, 0, 0);
              cmdPacketBuffer = new byte[CMD_LENGTH];
              inCmdStream.readFully(cmdPacketBuffer, 0, CMD_LENGTH);
              messageCmd = messageCmd.fromByteArray(cmdPacketBuffer);

              if (messageCmd.type == postvideo) {
                listensock = socketRec;
                fileTotalLength = messageCmd.length;
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
  public void seek(long seekPosition)
  {
    if (outStream == null)
      try {
        outStream = new DataOutputStream(listensock.getOutputStream());
      } catch (IOException e) {
        e.printStackTrace();
      }

    message_buffer_info messageCmd = new message_buffer_info(seekfile, (int)seekPosition,CMD_LENGTH);

    byte[] sendBuffer = messageCmd.toByteArray();
    try {
      outStream.write(sendBuffer, 0, CMD_LENGTH);
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  class message_buffer_info implements java.io.Serializable {
    public int type;//1 post mp4, 2 send mp4 file , 3 remote get file,;
      public int offset;
    public int length;
    message_buffer_info(int t, int f , int l)
    {
      type = t;
        offset = f;
      length = l;

    }
    public byte [] toByteArray() {

      ByteBuffer b = ByteBuffer.allocate(CMD_LENGTH);

      return b.putInt(type).putInt(offset).putInt(length).array();
    }

    public  message_buffer_info fromByteArray(byte [] bytes) {
      if (bytes.length < CMD_LENGTH) throw new IllegalArgumentException("not enough bytes");

      ByteBuffer b = ByteBuffer.wrap(bytes);
      int type = b.getInt();
        int offset  = b.getInt();
      int length = b.getInt();
      return new message_buffer_info(type, offset, length);
    }

    public  byte[] getBytes() throws java.io.IOException{
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeInt(type);
        oos.writeInt(offset);
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
      while (!Thread.currentThread().isInterrupted() && listensock!=null) {
        if ((packetBufferQue.size()>=MAX_QUEUE_SIZE)||
                (listensock ==null)||
                (bytesRemaining <=0 && fileTotalLength>0))
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

        message_buffer_info messageCmd = new message_buffer_info(getfile, fileOffset, DEFAULT_READ_PACKET_SIZE);

        byte[] sendBuffer = messageCmd.toByteArray();
        try {
          outStream.write(sendBuffer, 0, CMD_LENGTH);
        } catch (IOException e) {
          e.printStackTrace();
          break;
        }

        message_buffer_info recvCmd = new message_buffer_info(0, 0, 0);
        cmdPacketBuffer = new byte[CMD_LENGTH];
        try {
          inCmdStream.readFully(cmdPacketBuffer, 0, CMD_LENGTH);
        } catch (IOException e) {
          e.printStackTrace();
          break;
        }
        recvCmd = recvCmd.fromByteArray(cmdPacketBuffer);

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
                if(recvCmd.offset != (int)fileOffset)
                {
                    Log.d(LOGTAG,"drop for offset = " + recvCmd.offset);
                    continue;//maybe last time remaining
                }
                Log.d(LOGTAG, "remote package  arrive" +
                        " length =" + recvCmd.length +
                        " offset = "+ recvCmd.offset) ;
                  byte[] tempBuffer = new byte[(int) recvCmd.length];
                  System.arraycopy(packetBuffer, 0, tempBuffer, 0, (int) recvCmd.length);
                  packetBufferQue.put(tempBuffer);
                  bytesRemaining -= recvCmd.length;
                  Log.d(LOGTAG, "======bytesRemaining = " + bytesRemaining +
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
  private InetAddress getInetAddress()
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
}
