package com.slothbucket.blackduck.client;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.slothbucket.blackduck.common.AndroidUtils;
import com.slothbucket.blackduck.common.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages I/O with Bluetooth service.
 */
abstract class ServiceConnection extends Thread {

    private static final String TAG = AndroidUtils.tagNameFor(ServiceRequest.class);

    private final Queue<ServiceRequest> pendingRequests = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BluetoothDevice device;
    private final UUID serviceUuid;
    private final MessageIoBridge ioBridge;

    ServiceConnection(
            BluetoothDevice device, UUID serviceUuid, MessageIoBridge ioBridge) {
        super();
        this.device = Preconditions.checkNotNull(device);
        this.serviceUuid = Preconditions.checkNotNull(serviceUuid);
        this.ioBridge = Preconditions.checkNotNull(ioBridge);
    }

    /**
     * Queues a request to be sent.
     *
     * @return true if request is queued for dispatch
     */
    public boolean sendRequest(ServiceRequest request) {
        if (!shutdown.get()) {
            pendingRequests.offer(request);
            return true;
        }

        return false;
    }

    /**
     * Signals that dispatcher thread should be shutdown. Will no longer accept any new requests.
     */
    public void signalShutdown() {
        pendingRequests.offer(null);
        shutdown.compareAndSet(false, true);
    }

    /**
     * Handle device connection event.
     */
    abstract void onDeviceConnected();

    /**
     * Handle response from service.
     */
    abstract void onServiceResponse(ServiceResponse response);

    @Override
    public void run() {
        BluetoothSocket socket;
        try {
            socket = device.createRfcommSocketToServiceRecord(serviceUuid);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create RFCOMM connection to Bluetooth service.", e);
            return;
        }
        onDeviceConnected();

        InputStream inputStream;
        OutputStream outputStream;

        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            tryCloseSocket(socket);
            Log.e(TAG, "Failed to establish I/O with socket.", e);
            return;
        }

        try {
            while (true) {
                ServiceRequest request = pendingRequests.poll();
                if (request == null) {
                    Log.i(TAG, "Stopping request dispatcher: received termination signal.");
                    return;
                }

                ioBridge.write(request, outputStream);
                onServiceResponse(ioBridge.read(inputStream));
            }
        } finally {
            tryCloseSocket(socket);
        }
    }

    private void tryCloseSocket(BluetoothSocket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            Log.w(TAG, "Could not close socket. /shrug/", e);
        }
    }
}
