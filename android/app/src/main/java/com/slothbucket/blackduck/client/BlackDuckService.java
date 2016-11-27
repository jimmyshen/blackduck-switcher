package com.slothbucket.blackduck.client;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;

import com.slothbucket.blackduck.common.FluentLog;

import java.io.IOException;
import java.util.UUID;

public class BlackDuckService extends Service {
    private static final FluentLog logger =
        FluentLog.loggerFor("blackduck", BlackDuckService.class);
    private static final UUID SERVICE_UUID =
        UUID.fromString("7f759fe2-b22a-11e6-ba35-37c9859e1514");

    private Looper looper;
    private ServiceConnectionHandler connectionHandler;
    private LocalBroadcastManager broadcastManager;

    public static void sendRequest(Context context, ServiceRequest request) {
        Intent intent = new Intent(context, BlackDuckService.class);
        intent.setAction(Constants.ACTION_SERVICE_REQUEST);
        intent.putExtra(Constants.EXTRA_SERVICE_REQUEST, request);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        HandlerThread handlerThread =
            new HandlerThread("BlackDuckService", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();

        looper = handlerThread.getLooper();
        broadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.atDebug().log("Starting service.");
        if (intent != null) {
            String action = intent.getAction();
            logger.atDebug().log("Received service service action: %s", action);
            if (Constants.ACTION_CONNECT_DEVICE.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(Constants.EXTRA_DEVICE);
                BluetoothSocket socket;
                try {
                    socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                    socket.connect();

                    broadcastManager.sendBroadcast(new Intent(Constants.ACTION_DEVICE_CONNECTED));

                    connectionHandler =
                            new ServiceConnectionHandler(looper, socket, new MessagePackIoBridge()) {
                                @Override
                                void onServiceResponse(ServiceResponse response) {
                                    Intent intent = new Intent(Constants.ACTION_SERVICE_RESPONSE);
                                    intent.putExtra(Constants.EXTRA_SERVICE_RESPONSE, response);
                                    broadcastManager.sendBroadcast(intent);
                                }
                            };

                    logger.atInfo().log(
                        "Established connection with device %s", device.getAddress());
                } catch (IOException e) {
                    logger.atError().withCause(e).log(
                        "Failed to establish Bluetooth connection with device.");
                    onDeviceError(e);
                }
            } else if (Constants.ACTION_SERVICE_REQUEST.equals(action)) {
                ServiceRequest request = intent.getParcelableExtra(Constants.EXTRA_SERVICE_REQUEST);
                if (connectionHandler != null) {
                    connectionHandler.sendRequest(request);
                } else {
                    logger.atError().log(
                        "Attempted to send request without a connection available.");
                }
            } else {
                logger.atWarning().log("Unhandled action sent to service: %s", action);
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Do not bind this service.
        return null;
    }

    @Override
    public void onDestroy() {
        if (connectionHandler != null) {
            logger.atDebug().log("Closing connection.");
            connectionHandler.close();
        }
    }

    private void onDeviceError(Throwable cause) {
        Intent intent = new Intent(Constants.ACTION_DEVICE_ERROR);
        intent.putExtra(Constants.EXTRA_ERROR_MESSAGE, cause.getMessage());
        broadcastManager.sendBroadcast(intent);
    }
}
