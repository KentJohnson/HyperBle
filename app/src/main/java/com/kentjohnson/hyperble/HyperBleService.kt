package com.kentjohnson.hyperble

import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.kentjohnson.hyperble.HyperBleService.ServiceStates.*
import com.kentjohnson.hyperble.utils.HyperBleThrowable
import com.kentjohnson.hyperble.utils.HyperBleThrowable.Companion.ERROR_CODE_BAD_SVC_STATE
import com.kentjohnson.hyperble.utils.HyperBleThrowable.Companion.ERROR_CODE_NO_CHAR_FOUND
import com.kentjohnson.hyperble.utils.HyperBleThrowable.Companion.ERROR_CODE_NO_CONNECTION
import com.kentjohnson.hyperble.utils.HyperBleThrowable.Companion.ERROR_CODE_NO_DESC_FOUND
import com.kentjohnson.hyperble.utils.getCharacteristic
import com.kentjohnson.hyperble.utils.getDescriptor
import io.reactivex.*
import io.reactivex.Observable
import java.util.*

class HyperBleService : Service() {
    private val mBinder = LocalBinder()
    private val mConnections: HashMap<BluetoothDevice, BluetoothGatt?> = hashMapOf()

    private var mServiceState: ServiceStates = IDLE
    private var mConnectionListeners: HashMap<BluetoothDevice, HyperBleCallbacks.ConnectionListener?> = hashMapOf()
    private var mNotificationListeners: HashMap<Pair<BluetoothDevice, UUID>, HyperBleCallbacks.NotificationListener?> = hashMapOf()
    private var mCancelCallback: HyperBleCallbacks.CancelCallback? = null
    private var mDisconnectCallback: Pair<BluetoothDevice, HyperBleCallbacks.DisconnectCallback>? = null
    private var mConnectCallback: Pair<BluetoothDevice, HyperBleCallbacks.ConnectCallback>? = null
    private var mDiscoverCallback: Pair<BluetoothDevice, HyperBleCallbacks.DiscoverCallback>? = null
    private var mReadCallback: Pair<BluetoothDevice, HyperBleCallbacks.ReadCallback>? = null
    private var mWriteCallback: Pair<BluetoothDevice, HyperBleCallbacks.WriteCallback>? = null
    private var mRssiCallback: Pair<BluetoothDevice, HyperBleCallbacks.RssiCallback>? = null

    private val mHandler = Handler(Looper.getMainLooper())
    private lateinit var mRunnable: Runnable

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        mConnections.keys.forEach(::close)
    }

    fun cancel(): Completable {
        return Completable.create { subscriber ->
            when (mServiceState) {
                IDLE -> subscriber.onComplete()
                CANCELLING -> subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
                else -> {
                    mHandler.removeCallbacks(mRunnable)
                    updateState(CANCELLING)
                    clearCallbacks()
                    mCancelCallback = object: HyperBleCallbacks.CancelCallback {
                        override fun onCancel() {
                            subscriber.onComplete()
                        }
                    }
                }
            }
        }
    }

    fun isDeviceConnected(device: BluetoothDevice): Boolean = mConnections[device] != null

    fun isDeviceDiscovered(device: BluetoothDevice): Boolean = mConnections[device]?.services?.isNotEmpty() ?: false

    fun listenToConnection(device: BluetoothDevice): Observable<Boolean> {
        return Observable.create { subscriber ->
            mConnectionListeners[device] = object: HyperBleCallbacks.ConnectionListener {
                override fun onConnectionUpdate(connected: Boolean) {
                    subscriber.onNext(connected)
                }
            }
            subscriber.onNext(isDeviceConnected(device))
        }
    }

    fun clearConnectionListener(device: BluetoothDevice) {
        mConnectionListeners[device] = null
    }

    fun connect(device: BluetoothDevice, delay: Long = 500): Completable {
        return Completable.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let {
                    subscriber.onComplete()
                } ?: run {
                    delay(delay, Runnable {
                        updateState(CONNECTING)
                        device.connectGatt(this, false, GattCallback())?.let {
                            mConnections[device] = it
                        } ?: run{
                            updateState(IDLE)
                            subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
                        }
                    })
                }
            }
        }
    }

    fun disconnect(device: BluetoothDevice, delay: Long = 500): Completable {
        return Completable.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let {
                    delay(delay, Runnable {
                        updateState(DISCONNECTING, device, subscriber)
                        mConnections[device]?.disconnect() ?: subscriber.onComplete()
                    })
                } ?: run{
                    updateState(IDLE)
                    subscriber.onComplete()
                }
            }
        }.doOnComplete { close(device) }
    }

    fun discover(device: BluetoothDevice, delay: Long = 500): Single<List<BluetoothGattService>> {
        return Single.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let { gatt ->
                    if (gatt.services.isNotEmpty()) {
                        subscriber.onSuccess(gatt.services)
                    } else {
                        delay(delay, Runnable {
                            updateState(DISCOVERING, device, subscriber)
                            mConnections[device]?.discoverServices() ?: run {
                                updateState(IDLE)
                                subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
                            }
                        })
                    }
                } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
            }
        }
    }

    fun readCharacteristic(device: BluetoothDevice, uuid: UUID, delay: Long = 500): Single<ByteArray> {
        return Single.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let { gatt ->
                    gatt.getCharacteristic(uuid)?.let { char ->
                        delay(delay, Runnable {
                            updateState(READING, device, subscriber)
                            mConnections[device]?.readCharacteristic(char) ?: run {
                                updateState(IDLE)
                                subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
                            }
                        })
                    } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CHAR_FOUND))
                } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
            }
        }
    }

    fun writeCharacteristic(device: BluetoothDevice, uuid: UUID, bytes: ByteArray, delay: Long = 500): Completable {
        return Completable.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let { gatt ->
                    gatt.getCharacteristic(uuid)?.let { char ->
                        delay(delay, Runnable {
                            updateState(WRITING, device, subscriber)
                            mConnections[device]?.writeCharacteristic(char.apply { value = bytes }) ?: run {
                                updateState(IDLE)
                                subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
                            }
                        })
                    } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CHAR_FOUND))
                } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
            }
        }
    }

    fun enableNotifications(
        device: BluetoothDevice,
        uuid: UUID,
        enable: Boolean,
        delay: Long = 500
    ): Observable<ByteArray> {
        return Observable.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let { gatt ->
                    gatt.getCharacteristic(uuid)?.let { char ->
                        delay(delay, Runnable {
                            if(enable) {
                                mNotificationListeners[Pair(device, uuid)] =
                                    object : HyperBleCallbacks.NotificationListener {
                                        override fun onCharacteristicChanged(bytes: ByteArray) {
                                            subscriber.onNext(bytes)
                                        }
                                    }
                            } else {
                                mNotificationListeners[Pair(device, uuid)] = null
                            }
                            mConnections[device]?.setCharacteristicNotification(char, enable) ?: run {
                                updateState(IDLE)
                                subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
                            }
                        })
                    } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CHAR_FOUND))
                } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
            }
        }
    }

    fun readDescriptor(device: BluetoothDevice, uuid: UUID, delay: Long = 500): Single<ByteArray> {
        return Single.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let { gatt ->
                    gatt.getDescriptor(uuid)?.let { desc ->
                        delay(delay, Runnable {
                            updateState(READING, device, subscriber)
                            mConnections[device]?.readDescriptor(desc) ?: run {
                                updateState(IDLE)
                                subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
                            }
                        })
                    } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_DESC_FOUND))
                } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
            }
        }
    }

    fun writeDescriptor(device: BluetoothDevice, uuid: UUID, bytes: ByteArray, delay: Long = 500): Completable {
        return Completable.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let { gatt ->
                    gatt.getDescriptor(uuid)?.let { desc ->
                        delay(delay, Runnable {
                            updateState(WRITING, device, subscriber)
                            mConnections[device]?.writeDescriptor(desc.apply { value = bytes }) ?: run {
                                updateState(IDLE)
                                subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
                            }
                        })
                    } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_DESC_FOUND))
                } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
            }
        }
    }

    fun readRssi(device: BluetoothDevice, delay: Long = 500): Single<Int> {
        return Single.create { subscriber ->
            if (mServiceState != IDLE) {
                subscriber.onError(HyperBleThrowable(ERROR_CODE_BAD_SVC_STATE))
            } else {
                mConnections[device]?.let {
                    delay(delay, Runnable {
                        updateState(READING, device, subscriber)
                        mConnections[device]?.readRemoteRssi() ?: run {
                            updateState(IDLE)
                            subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
                        }
                    })
                } ?: subscriber.onError(HyperBleThrowable(ERROR_CODE_NO_CONNECTION))
            }
        }
    }

    private fun close(device: BluetoothDevice) {
        mConnections[device]?.close()
        mConnections[device] = null
    }


    @Suppress("UNCHECKED_CAST")
    private fun updateState(state: ServiceStates, device: BluetoothDevice, emitter: SingleEmitter<*>) {
        updateState(state)
        when (state) {
            DISCOVERING -> {
                mDiscoverCallback = Pair(device, object: HyperBleCallbacks.DiscoverCallback {
                    override fun onDiscoverSuccess(services: List<BluetoothGattService>) {
                        (emitter as SingleEmitter<List<BluetoothGattService>>).onSuccess(services)
                    }

                    override fun onDiscoverFailure(errorCode: Int) {
                        emitter.onError(Throwable(HyperBleThrowable(errorCode)))
                    }
                })
            }
            READING -> {
                mReadCallback = Pair(device, object: HyperBleCallbacks.ReadCallback {
                    override fun onReadSuccess(bytes: ByteArray) {
                        (emitter as SingleEmitter<ByteArray>).onSuccess(bytes)
                    }

                    override fun onReadFailure(errorCode: Int) {
                        emitter.onError(HyperBleThrowable(errorCode))
                    }
                })
            }
            else -> {
            }
        }
    }

    private fun updateState(state: ServiceStates, device: BluetoothDevice, emitter: CompletableEmitter) {
        updateState(state)
        when (state) {
            CONNECTING -> {
                mConnectCallback = Pair(device, object: HyperBleCallbacks.ConnectCallback {
                    override fun onConnectSuccess() {
                        emitter.onComplete()
                    }

                    override fun onConnectFailure(errorCode: Int) {
                        emitter.onError(HyperBleThrowable(errorCode))
                    }
                })
            }
            DISCONNECTING -> {
                mDisconnectCallback = Pair(device, object: HyperBleCallbacks.DisconnectCallback {
                    override fun onDisconnect() {
                        emitter.onComplete()
                    }
                })
            }
            WRITING -> {
                mWriteCallback = Pair(device, object: HyperBleCallbacks.WriteCallback {
                    override fun onWriteSuccess() {
                        emitter.onComplete()
                    }

                    override fun onWriteFailure(errorCode: Int) {
                        emitter.onError(HyperBleThrowable(errorCode))
                    }
                })
            }
            else -> {
            }
        }
    }

    private fun updateState(state: ServiceStates) {
        mServiceState = state
    }

    private fun delay(delay: Long, andDo: Runnable) {
        mRunnable = andDo
        mHandler.postDelayed(mRunnable, delay)
    }

    private fun clearCallbacks() {
        mCancelCallback = null
        mDisconnectCallback = null
        mConnectCallback = null
        mDiscoverCallback = null
        mReadCallback = null
        mWriteCallback = null
        mRssiCallback = null
    }

    inner class GattCallback : BluetoothGattCallback() {
        private fun ifNotCancelling(doThis: () -> Unit) {
            if (mServiceState != CANCELLING) {
                doThis()
            } else {
                updateState(IDLE)
                mCancelCallback?.onCancel()
            }
        }

        private fun ifState(state: ServiceStates, doThis: () -> Unit) {
            if (mServiceState == state) {
                updateState(IDLE)
                doThis()
            }
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mConnectionListeners[gatt.device]?.onConnectionUpdate(newState == STATE_CONNECTED)
            if (mServiceState != CANCELLING) {
                if (status == GATT_SUCCESS && newState == STATE_CONNECTED) {
                    ifState(CONNECTING) {
                        mConnectCallback?.takeIf { cb -> cb.first == gatt.device }?.second?.onConnectSuccess()
                    }
                } else {
                    val previousState = mServiceState
                    updateState(IDLE)
                    when (previousState) {
                        DISCONNECTING -> mDisconnectCallback?.takeIf { cb -> cb.first == gatt.device }?.second?.onDisconnect()
                        CONNECTING -> mConnectCallback?.takeIf { cb -> cb.first == gatt.device }?.second?.onConnectFailure(status)
                        DISCOVERING -> mDiscoverCallback?.takeIf { cb -> cb.first == gatt.device }?.second?.onDiscoverFailure(status)
                        READING -> mReadCallback?.takeIf { cb -> cb.first == gatt.device }?.second?.onReadFailure(status)
                        WRITING -> mWriteCallback?.takeIf { cb -> cb.first == gatt.device }?.second?.onWriteFailure(status)
                        else -> {
                        }
                    }
                    close(gatt.device)
                }
            } else {
                updateState(IDLE)
                mCancelCallback?.onCancel()
                if (status != GATT_SUCCESS) {
                    close(gatt.device)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            ifNotCancelling {
                ifState(DISCOVERING) {
                    val callback = mDiscoverCallback?.takeIf { cb -> cb.first == gatt.device }?.second
                    if (status == GATT_SUCCESS) {
                        callback?.onDiscoverSuccess(gatt.services)
                    } else {
                        callback?.onDiscoverFailure(status)
                    }
                }

            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            ifNotCancelling {
                ifState(READING) {
                    val callback = mReadCallback?.takeIf { cb -> cb.first == gatt.device }?.second
                    if (status == GATT_SUCCESS) {
                        callback?.onReadSuccess(characteristic.value)
                    } else {
                        callback?.onReadFailure(status)
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            ifNotCancelling {
                ifState(WRITING) {
                    val callback = mWriteCallback?.takeIf { cb -> cb.first == gatt.device }?.second
                    if (status == GATT_SUCCESS) {
                        callback?.onWriteSuccess()
                    } else {
                        callback?.onWriteFailure(status)
                    }
                }
            }
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            ifNotCancelling {
                ifState(READING) {
                    val callback = mReadCallback?.takeIf { cb -> cb.first == gatt.device }?.second
                    if (status == GATT_SUCCESS) {
                        callback?.onReadSuccess(descriptor.value)
                    } else {
                        callback?.onReadFailure(status)
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            ifNotCancelling {
                ifState(WRITING) {
                    val callback = mWriteCallback?.takeIf { cb -> cb.first == gatt.device }?.second
                    if (status == GATT_SUCCESS) {
                        callback?.onWriteSuccess()
                    } else {
                        callback?.onWriteFailure(status)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            mNotificationListeners[Pair(gatt.device, characteristic.uuid)]?.onCharacteristicChanged(characteristic.value)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            ifNotCancelling {
                ifState(READING) {
                    val callback = mRssiCallback?.takeIf { cb -> cb.first == gatt.device }?.second
                    if (status == GATT_SUCCESS) {
                        callback?.onReadSuccess(rssi)
                    } else {
                        callback?.onReadFailure(status)
                    }
                }
            }
        }
    }

    enum class ServiceStates {
        IDLE,
        CONNECTING,
        DISCONNECTING,
        DISCOVERING,
        READING,
        WRITING,
        CANCELLING
    }

    inner class LocalBinder : Binder() {
        fun getService(): HyperBleService = this@HyperBleService
    }
}