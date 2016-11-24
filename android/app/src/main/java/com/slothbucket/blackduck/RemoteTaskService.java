package com.slothbucket.blackduck;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.UUID;

/**
 * Manages interaction with BlackDuck task service over a persistent Bluetooth.
 */
public class RemoteTaskService extends IntentService {
    static final class Constants {
        private Constants() {}

        public static final String ACTION_CONNECT_SERVICE = pkgAction("CONNECT_SERVICE");
        public static final String ACTION_LIST_TASKS = pkgAction("LIST_TASKS");

        private static String pkgAction(String name) {
            return "com.slothbucket.blackduck.action." + name;
        }
    }

    private static final String TAG = "RemoteTaskService";
    private static final UUID SERVICE_UUID = UUID.fromString("7f759fe2-b22a-11e6-ba35-37c9859e1514");
    private static final String SERVICE_CHARSET = "UTF8";

    private class TaskServiceConnection extends Thread {

        private final BluetoothDevice device;
        @Nullable private BluetoothSocket socket;
        @Nullable private InputStreamReader reader;
        @Nullable private OutputStreamWriter writer;

        TaskServiceConnection(BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public void run() {
            socket = connectSocket(device);
            if (socket != null) {
                try {
                    writer = new OutputStreamWriter(socket.getOutputStream(), SERVICE_CHARSET);
                    reader = new InputStreamReader(socket.getInputStream(), SERVICE_CHARSET);
                } catch (IOException e) {
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // fml
                    }
                }
            }
        }

        @Nullable
        private static BluetoothSocket connectSocket(BluetoothDevice device) {
            try {
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                socket.connect();
                return socket;
            } catch (IOException e) {
                Log.e(TAG,
                    String.format("Failed to connect to service on device %s ('%s')",
                            device.getAddress(), device.getName()));
            }

            return null;
        }

        public void listTasks() {
        }

        public void dispatchResponse(byte[] bytes) {
        }
    }

    @Nullable private TaskServiceConnection connection;

    public RemoteTaskService() {
        super("RemoteTaskService");
    }

    /**
     * Connect to task service being hosted on the given device.
     *
     * <p>Be sure to call {@link BluetoothAdapter#cancelDiscovery()} to reduce load on the adapter
     * before connecting.
     */
    public static void connectService(Context context, BluetoothDevice device) {
    }

    /**
     * TODO
     */
    public static void listTasks(Context context) {
        Intent intent = new Intent(context, RemoteTaskService.class);
        intent.setAction(Constants.ACTION_LIST_TASKS);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        final String action = intent.getAction();
        if (Constants.ACTION_LIST_TASKS.equals(action)) {
            onListTasksAction();
        }
    }

    private void onConnectServiceAction(BluetoothDevice device) {
        if (connection != null) {
            throw new IllegalStateException(
                "Cannot connect to service: there is an existing connection!");
        }

        connection = new TaskServiceConnection(device);
        connection.start();
    }

    private void onListTasksAction() {
        if (connection == null) {
            throw new IllegalStateException("Cannot list tasks: no connection was established.");
        }
    }
}
