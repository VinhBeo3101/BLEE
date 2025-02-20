package com.vinhbeo.blelibrary;


import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;


    /**
     * Service for managing connection and data communication with a GATT server hosted on a
     * given Bluetooth LE device.
     */
    public class BLEServices extends Service
    {

        private final static String TAG = BLEServices.class.getSimpleName();

        private BluetoothManager mBluetoothManager;
        private BluetoothAdapter mBluetoothAdapter;
        private String mBluetoothDeviceAddress;
        private BluetoothGatt mBluetoothGatt;
        private int mConnectionState = STATE_DISCONNECTED;

        private static final int STATE_DISCONNECTED = 0;
        private static final int STATE_CONNECTING = 1;
        private static final int STATE_CONNECTED = 2;

        public final static String ACTION_GATT_CONNECTED =
                "android-er.ACTION_GATT_CONNECTED";
        public final static String ACTION_GATT_DISCONNECTED =
                "android-er.ACTION_GATT_DISCONNECTED";
        public final static String ACTION_GATT_SERVICES_DISCOVERED =
                "android-er.ACTION_GATT_SERVICES_DISCOVERED";
        public final static String ACTION_DATA_AVAILABLE =
                "android-er.ACTION_DATA_AVAILABLE";
        public final static String EXTRA_DATA =
                "android-er.EXTRA_DATA";

        public final static UUID UUID_BLE_SHIELD_TX = UUID
                .fromString(BLEAttribute.BLE_SHIELD_TX);
        public final static UUID UUID_BLE_SHIELD_RX = UUID
                .fromString(BLEAttribute.BLE_SHIELD_RX);
        public final static UUID UUID_BLE_SHIELD_SERVICE = UUID
                .fromString(BLEAttribute.BLE_SHIELD_SERVICE);

        // Implements callback methods for GATT events that the app cares about.  For example,
        // connection change and services discovered.
        private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                String intentAction;
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    intentAction = ACTION_GATT_CONNECTED;
                    mConnectionState = STATE_CONNECTED;
                    broadcastUpdate(intentAction);
                    Log.i(TAG, "Connected to GATT server.");
                    // Attempts to discover services after successful connection.
                    Log.i(TAG, "Attempting to start service discovery:" +
                            mBluetoothGatt.discoverServices());

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    intentAction = ACTION_GATT_DISCONNECTED;
                    mConnectionState = STATE_DISCONNECTED;
                    Log.i(TAG, "Disconnected from GATT server.");
                    broadcastUpdate(intentAction);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                } else {
                    Log.w(TAG, "onServicesDiscovered received: " + status);
                }
            }
            //This is used to read data from the BLE device The callback is called when you write this code
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt,
                                             BluetoothGattCharacteristic characteristic,
                                             int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }
            }
            //This is used to send data to the BLE device, usually in data mode for the BLE device
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt,
                                              BluetoothGattCharacteristic characteristic, int status) {
                Log.e(TAG, "on write = " +status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }
            }
            //This callback is called when you are trying to send data using writeCharacteristic(characteristics) and the BLE device responds with some value

            // NOTIFY(CallBack moi lan co du lieu tra ve)
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

            }
        };

        private void broadcastUpdate(final String action) {
            final Intent intent = new Intent(action);
            sendBroadcast(intent);
        }

        private void broadcastUpdate(final String action,
                                     final BluetoothGattCharacteristic characteristic) {
            final Intent intent = new Intent(action);

            Log.w(TAG, "broadcastUpdate()");
            if(UUID_BLE_SHIELD_RX.equals(characteristic.getUuid())) {
                final byte[] data = characteristic.getValue();

                Log.v(TAG, "data.length: " + data.length);

                if (data != null && data.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data) {
                        stringBuilder.append(String.format("%02X ", byteChar));

                        Log.v(TAG, String.format("%02X ", byteChar));
                    }
                    intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
                }
            }

            sendBroadcast(intent);
        }

        public class LocalBinder extends Binder {
            BLEServices getService() {
                return BLEServices.this;
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return mBinder;
        }

        @Override
        public boolean onUnbind(Intent intent) {
            // After using a given device, you should make sure that BluetoothGatt.close() is called
            // such that resources are cleaned up properly.  In this particular example, close() is
            // invoked when the UI is disconnected from the Service.
            close();
            return super.onUnbind(intent);
        }

        private final IBinder mBinder = new LocalBinder();

        /**
         * Initializes a reference to the local Bluetooth adapter.
         *
         * @return Return true if the initialization is successful.
         */
        public boolean initialize() {
            // For API level 18 and above, get a reference to BluetoothAdapter through
            // BluetoothManager.
            if (mBluetoothManager == null) {
                mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                if (mBluetoothManager == null) {
                    Log.e(TAG, "Unable to initialize BluetoothManager.");
                    return false;
                }
            }

            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if (mBluetoothAdapter == null) {
                Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
                return false;
            }

            return true;
        }

        /**
         * Connects to the GATT server hosted on the Bluetooth LE device.
         *
         * @param address The device address of the destination device.
         *
         * @return Return true if the connection is initiated successfully. The connection result
         *         is reported asynchronously through the
         *         {@code BluetoothGattCallback#onConnectionStateChange(
         *         android.bluetooth.BluetoothGatt, int, int)}
         *         callback.
         */
        public boolean connect(final String address) {
            if (mBluetoothAdapter == null || address == null) {
                Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
                return false;
            }

            // Previously connected device.  Try to reconnect.
            if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                    && mBluetoothGatt != null) {
                Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
                if (mBluetoothGatt.connect()) {
                    mConnectionState = STATE_CONNECTING;
                    return true;
                } else {
                    return false;
                }
            }

            final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                Log.w(TAG, "Device not found.  Unable to connect.");
                return false;
            }
            // We want to directly connect to the device, so we are setting the autoConnect
            // parameter to false.
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
            Log.d(TAG, "Trying to create a new connection.");
            mBluetoothDeviceAddress = address;
            mConnectionState = STATE_CONNECTING;
            return true;
        }

        /**
         * Disconnects an existing connection or cancel a pending connection. The disconnection result
         * is reported asynchronously through the
         * {@code BluetoothGattCallback#onConnectionStateChange(
         * android.bluetooth.BluetoothGatt, int, int)}
         * callback.
         */
        public void disconnect() {
            if (mBluetoothAdapter == null || mBluetoothGatt == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
            }
            mBluetoothGatt.disconnect();
        }

        /**
         * After using a given BLE device, the app must call this method to ensure resources are
         * released properly.
         */

        // close the client app
        public void close() {
            if (mBluetoothGatt == null) {
                return;
            }
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        /**
         * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
         * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(
         * android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
         * callback.
         *
         * @param characteristic The characteristic to read from.
         */

        // Read characteristic
        public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {


            if (mBluetoothAdapter == null || mBluetoothGatt == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return false;
            }
            return mBluetoothGatt.readCharacteristic(characteristic);
        }


        // Write characteristic
        public boolean writeCharacteristic(byte[] data)
        {
            if (mBluetoothGatt == null) {
                Log.e(TAG, "lost connection");
                return false;
            }
            BluetoothGattService Service = mBluetoothGatt.getService(UUID.fromString(BLEAttribute.BLE_SHIELD_SERVICE));
            if (Service == null) {
                Log.e(TAG, "service not found!");
                return false;
            }
            BluetoothGattCharacteristic characteristic = Service
                    .getCharacteristic(UUID.fromString(BLEAttribute.BLE_SHIELD_RX));
            if (characteristic == null) {
                Log.e(TAG, "char not found!");
                return false;
            }

            characteristic.setValue(data);
            boolean status = mBluetoothGatt.writeCharacteristic(characteristic);
            Log.e(TAG, "write =" + status);
            return status;

        }

        /**
         * Enables or disables notification on a give characteristic.
         *
         * @param characteristic Characteristic to act on.
         * @param enabled If true, enable notification.  False otherwise.
         */
        public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                                  boolean enabled) {
            if (mBluetoothAdapter == null || mBluetoothGatt == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
            }
            Log.e(TAG, "setCharacteristicNotification");
            mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);


            UUID uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(uuid);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);

        }

        /**
         * Retrieves a list of supported GATT services on the connected device. This should be
         * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
         *
         * @return A {@code List} of supported services.
         */
        public List<BluetoothGattService> getSupportedGattServices() {
            if (mBluetoothGatt == null) return null;

            return mBluetoothGatt.getServices();
        }


    }

