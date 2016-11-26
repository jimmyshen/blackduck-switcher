package com.slothbucket.blackduck.client;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.slothbucket.blackduck.common.AndroidUtils;
import com.slothbucket.blackduck.common.Preconditions;

import java.io.IOException;
import java.util.UUID;

/**
 * Manages I/O with Bluetooth service.
 */
abstract class ServiceConnectionHandler extends Handler {

    private static final String TAG = AndroidUtils.tagNameFor(ServiceConnectionHandler.class);

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
                try {
                    ioBridge.write(request, socket.getOutputStream());
                    onServiceResponse(ioBridge.read(socket.getInputStream()));
                } catch (IOException e) {
                    Log.e(TAG,
                        String.format("Failed to process request %d", request.requestId()), e);
                }
            }
        });
    }

    void close() {
        try {
            socket.close();
        } catch (IOException e) {
            Log.w(TAG, "Failed to close socket.", e);
        }
    }

    abstract void onServiceResponse(ServiceResponse response);
}
