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
import android.util.Log;

import com.slothbucket.blackduck.common.AndroidUtils;

import java.io.IOException;
import java.util.UUID;

public class BlackDuckService extends Service {
    private static final String TAG = AndroidUtils.tagNameFor(BlackDuckService.class);
    private static final UUID SERVICE_UUID = UUID.fromString("7f759fe2-b22a-11e6-ba35-37c9859e1514");

    private Looper looper;
    private ServiceConnectionHandler connectionHandler;
    private LocalBroadcastManager broadcastManager;

    public static void sendRequest(Context context, ServiceRequest request) {
        Intent intent = new Intent(Constants.ACTION_SERVICE_REQUEST);
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
        Log.d(TAG, "Starting BlackDuck service.");
        if (intent != null) {
            String action = intent.getAction();
            if (Constants.ACTION_CONNECT_DEVICE.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(Constants.EXTRA_DEVICE);
                BluetoothSocket socket;
                try {
                    socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                    broadcastManager.sendBroadcast(new Intent(Constants.ACTION_DEVICE_CONNECTED));

                    connectionHandler =
                            new ServiceConnectionHandler(looper, socket, new MessagePackIoBridge()) {
                                @Override
                                void onServiceResponse(ServiceResponse response) {
                                    Intent intent = new Intent(Constants.ACTION_SERVICE_RESPONSE);
                                    intent.putExtra(Constants.ACTION_SERVICE_RESPONSE, response);
                                    broadcastManager.sendBroadcast(intent);
                                }
                            };
                } catch (IOException e) {
                    Log.e(TAG, "Failed to establish Bluetooth connection with device.", e);
                }
            } else if (Constants.ACTION_SERVICE_REQUEST.equals(action)) {
                ServiceRequest request = intent.getParcelableExtra(Constants.EXTRA_SERVICE_REQUEST);
                if (connectionHandler != null) {
                    connectionHandler.sendRequest(request);
                } else {
                    Log.e(TAG, "Attempted to send request without a connection available.");
                }
            } else {
                Log.w(TAG, String.format("Unhandled action sent to service: %s", action));
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
            connectionHandler.close();
        }
    }
}
