package com.kentjohnson.hyperble.utils
import android.bluetooth.BluetoothGatt.*
import android.os.Build
import android.support.annotation.RequiresApi

enum class GattStatuses(val id: Int){
    SUCCESS(GATT_SUCCESS),
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    CONNECTION_CONGESTED(GATT_CONNECTION_CONGESTED),
    INSUFFICIENT_AUTHENTICATION(GATT_INSUFFICIENT_AUTHENTICATION),
    INSUFFICIENT_ENCRYPTION(GATT_INSUFFICIENT_ENCRYPTION),
    INVALID_ATTRIBUTE_LENGTH(GATT_INVALID_ATTRIBUTE_LENGTH),
    READ_NOT_PERMITTED(GATT_READ_NOT_PERMITTED),
    WRITE_NOT_PERMITTED(GATT_WRITE_NOT_PERMITTED),
    REQUEST_NOT_SUPPORTED(GATT_REQUEST_NOT_SUPPORTED),
    FAILURE(GATT_FAILURE);

    companion object {
        fun decode(status: Int): String {
            return values().associateBy(GattStatuses::id)[status]?.name ?: "Unknown Gatt Status Code: $status"
        }
    }
}