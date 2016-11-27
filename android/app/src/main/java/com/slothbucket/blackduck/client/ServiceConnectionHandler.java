package com.slothbucket.blackduck.client;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import com.slothbucket.blackduck.common.FluentLog;
import com.slothbucket.blackduck.common.Preconditions;

import java.io.IOException;

/**
 * Manages I/O with Bluetooth service.
 */
abstract class ServiceConnectionHandler extends Handler {

    private static final FluentLog logger = FluentLog.loggerFor(ServiceConnectionHandler.class);

    private final BluetoothSocket socket;
    private final MessageIoBridge ioBridge;

    ServiceConnectionHandler(Looper looper, BluetoothSocket socket, MessageIoBridge ioBridge) {
        super(looper);
        this.socket = Preconditions.checkNotNull(socket);
        this.ioBridge = Preconditions.checkNotNull(ioBridge);
    }

    boolean sendRequest(final ServiceRequest request) {
        return post(new Runnable() {

            @Override
            public void run() {
                int requestId = request.requestId();
                try {
                    logger.atDebug().log("Sending request %d: %s", requestId, request);
                    ioBridge.write(request, socket.getOutputStream());
                    logger.atDebug().log("Request %d sent successfully.", requestId);
                } catch (IOException e) {
                    logger.atError().withCause(e).log("Failed to process request %d", requestId);
                }

                try {
                    ServiceResponse response = ioBridge.read(socket.getInputStream());
                    logger.atDebug().log(
                            "Received response for request %d: %s", requestId, response);
                    onServiceResponse(response);
                } catch (IOException e) {
                    logger.atError().withCause(e).log("Failed to process request %d", requestId);
                }

            }
        });
    }

    void close() {
        try {
            socket.close();
        } catch (IOException e) {
            logger.atWarning().withCause(e).log("Failed to close socket.");
        }
    }

    abstract void onServiceResponse(ServiceResponse response);
}
