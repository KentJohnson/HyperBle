package com.kentjohnson.hyperble

import android.bluetooth.BluetoothGattService

object HyperBleCallbacks {
    interface CancelCallback {
        fun onCancel()
    }

    interface DisconnectCallback {
        fun onDisconnect()
    }

    interface ConnectCallback {
        fun onConnectSuccess()
        fun onConnectFailure(errorCode: Int)
    }

    interface DiscoverCallback {
        fun onDiscoverSuccess(services: List<BluetoothGattService>)
        fun onDiscoverFailure(errorCode: Int)
    }

    interface ReadCallback {
        fun onReadSuccess(bytes: ByteArray)
        fun onReadFailure(errorCode: Int)
    }

    interface WriteCallback {
        fun onWriteSuccess()
        fun onWriteFailure(errorCode: Int)
    }

    interface RssiCallback {
        fun onReadSuccess(rssi: Int)
        fun onReadFailure(errorCode: Int)
    }

    interface ConnectionListener {
        fun onConnectionUpdate(connected: Boolean)
    }

    interface NotificationListener {
        fun onCharacteristicChanged(bytes: ByteArray)
    }
}