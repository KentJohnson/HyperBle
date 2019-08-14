package com.kentjohnson.hyperble.utils

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.*

fun BluetoothGatt.getCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
    services.forEach { service ->
        service.getCharacteristic(uuid)?.let { return it }
    }
    return null
}

fun BluetoothGatt.getDescriptor(uuid: UUID): BluetoothGattDescriptor? {
    services.forEach { service ->
        service.characteristics.forEach { char ->
            char.getDescriptor(uuid)?.let { return it }
        }
    }
    return null
}