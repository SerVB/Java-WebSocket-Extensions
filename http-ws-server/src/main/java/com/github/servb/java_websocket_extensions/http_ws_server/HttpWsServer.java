package com.github.servb.java_websocket_extensions.http_ws_server;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.WebSocketServerFactory;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.CloseHandshakeType;
import org.java_websocket.enums.HandshakeState;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.util.Charsetfunctions;

public abstract class HttpWsServer {

    private static final String PATH_FIELD = "X-My-Path";  // for draft
    private static final String NO_FRAMES_MESSAGE = "This draft doesn't work with frames";  // for draft
    private static final String NO_CLIENT_MESSAGE = "This draft can't be used on a client";  // for draft

    private static final Object HTTP_CONNECTION_ATTACHMENT = new Object();
    private final HttpWebSocketServer webSocketServer;

    HttpWsServer(final int port) {
        webSocketServer = new HttpWebSocketServer(port);
    }

    private static void onlyForWs(final WebSocket connection, final Runnable block) {
        if (connection.getAttachment() != HTTP_CONNECTION_ATTACHMENT) {
            block.run();
        }
    }

    private static boolean isHttp(final ClientHandshake clientHandshake) {
        final String upgradeHeader = clientHandshake.getFieldValue("Upgrade");
        return upgradeHeader == null || upgradeHeader.trim().isEmpty();
    }

    public final Boolean wasStarted() {
        return webSocketServer.wasStarted();
    }

    public final void start() {
        webSocketServer.start();
    }

    public final void stop(final int timeout) throws InterruptedException {
        webSocketServer.stop(timeout);
    }

    public final void stop() throws InterruptedException {
        stop(0);
    }

    public final void forEachOpenedConnection(final Consumer<WebSocket> action) {
        for (final WebSocket connection : webSocketServer.getConnections()) {
            if (connection.isOpen()) {
                action.accept(connection);
            }
        }
    }

    public final void setWebSocketFactory(final WebSocketServerFactory factory) {
        webSocketServer.setWebSocketFactory(factory);
    }

    public abstract void onServerStart();

    public abstract void onServerError(final WebSocket connection, final Exception e);

    public abstract void onWsOpen(final WebSocket connection);

    public abstract void onWsClose(final WebSocket connection);

    public abstract void onWsMessage(final WebSocket connection, final String message);

    public abstract void onWsMessage(final WebSocket connection, final ByteBuffer message);

    public abstract GetRequestResult onGetRequest(final String path);

    private final class HttpDraft extends Draft {

        @Override
        public HandshakeState acceptHandshakeAsServer(final ClientHandshake clientHandshake) {
            if (isHttp(clientHandshake)) {
                return HandshakeState.MATCHED;
            } else {
                return HandshakeState.NOT_MATCHED;
            }
        }

        @Override
        public HandshakeBuilder postProcessHandshakeResponseAsServer(final ClientHandshake request, final ServerHandshakeBuilder response) {
            response.put(PATH_FIELD, request.getResourceDescriptor());
            return response;
        }

        @Override
        public List<ByteBuffer> createHandshake(final Handshakedata handshakedata, final boolean withcontent) {
            final GetRequestResult result = onGetRequest(handshakedata.getFieldValue(PATH_FIELD));

            final byte[] header = Charsetfunctions.asciiBytes(
                    "HTTP/1.0 " + result.getStatusCode() + " " + result.getStatusText() + "\r\n" +
                            "Mime-Version: 1.0\r\n" +
                            "Content-Type: " + result.getContentType() + "r\n" +
                            "Content-Length: " + result.getContent().length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            );

            final ByteBuffer byteBuffer = ByteBuffer.allocate(result.getContent().length + header.length);
            byteBuffer.put(header);
            byteBuffer.put(result.getContent());
            byteBuffer.flip();

            return Collections.singletonList(byteBuffer);
        }

        @Override
        public CloseHandshakeType getCloseHandshakeType() {
            return CloseHandshakeType.NONE;
        }

        @Override
        public Draft copyInstance() {
            return this;
        }

        @Override
        public void reset() {
            // nop
        }

        @Override
        public List<Framedata> translateFrame(final ByteBuffer buffer) {
            throw new UnsupportedOperationException(NO_FRAMES_MESSAGE);
        }

        @Override
        public void processFrame(final WebSocketImpl webSocketImpl, final Framedata frame) {
            throw new UnsupportedOperationException(NO_FRAMES_MESSAGE);
        }

        @Override
        public ByteBuffer createBinaryFrame(final Framedata framedata) {
            throw new UnsupportedOperationException(NO_FRAMES_MESSAGE);
        }

        @Override
        public List<Framedata> createFrames(final ByteBuffer binary, final boolean mask) {
            throw new UnsupportedOperationException(NO_FRAMES_MESSAGE);
        }

        @Override
        public List<Framedata> createFrames(final String text, final boolean mask) {
            throw new UnsupportedOperationException(NO_FRAMES_MESSAGE);
        }

        @Override
        public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(final ClientHandshakeBuilder request) {
            throw new UnsupportedOperationException(NO_CLIENT_MESSAGE);
        }

        @Override
        public HandshakeState acceptHandshakeAsClient(final ClientHandshake request, final ServerHandshake response) {
            throw new UnsupportedOperationException(NO_CLIENT_MESSAGE);
        }
    }

    private final class HttpWebSocketServer extends WebSocketServer {

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private volatile Boolean wasInitialized = null;

        {
            setReuseAddr(true);
            setTcpNoDelay(true);
        }

        public HttpWebSocketServer(final int port) {
            super(new InetSocketAddress(port), Arrays.asList(new HttpDraft(), new Draft_6455()));
        }

        public final boolean wasStarted() {
            while (wasInitialized == null) {
                lock.lock();
                if (wasInitialized == null) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    lock.unlock();
                    return wasInitialized;
                }
                lock.unlock();
            }

            return wasInitialized;
        }

        @Override
        public final void onOpen(final WebSocket conn, final ClientHandshake handshake) {
            if (isHttp(handshake)) {
                conn.setAttachment(HTTP_CONNECTION_ATTACHMENT);
                conn.close();
            } else {
                onWsOpen(conn);
            }
        }

        @Override
        public final void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
            onlyForWs(conn, () -> onWsClose(conn));
        }

        @Override
        public final void onMessage(final WebSocket conn, final String message) {
            onlyForWs(conn, () -> onWsMessage(conn, message));
        }

        @Override
        public final void onMessage(final WebSocket conn, final ByteBuffer message) {
            onWsMessage(conn, message);
        }

        @Override
        public final void onError(final WebSocket conn, final Exception ex) {
            onServerError(conn, ex);

            if (ex instanceof SocketException) {
                lock.lock();
                wasInitialized = false;
                condition.signal();
                lock.unlock();
            }
        }

        @Override
        public final void onStart() {
            onServerStart();

            lock.lock();
            wasInitialized = true;
            condition.signal();
            lock.unlock();
        }
    }


}
