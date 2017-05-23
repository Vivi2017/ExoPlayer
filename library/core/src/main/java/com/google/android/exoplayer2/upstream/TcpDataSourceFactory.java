package com.google.android.exoplayer2.upstream;

/**
 * Created by vivisong on 11/05/2017.
 */

public class TcpDataSourceFactory implements DataSource.Factory {
    private final TransferListener<? super TcpDataSource> listener;
    private final String userAgent;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final boolean allowCrossProtocolRedirects;

    public TcpDataSourceFactory(String userAgent) {
        this(userAgent, null);
    }

    public TcpDataSourceFactory(String userAgent, TransferListener<? super DataSource> listener) {
        this(userAgent, listener, TcpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                TcpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, false);
    }

    public TcpDataSourceFactory(String userAgent,
                                        TransferListener<? super DataSource> listener, int connectTimeoutMillis,
                                        int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this.userAgent = userAgent;
        this.listener = listener;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
    }

    @Override
    public DataSource createDataSource() {
        return new TcpDataSource(listener);
    }
}

