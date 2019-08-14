package com.kentjohnson.hyperble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.Handler
import android.support.annotation.RequiresApi
import com.kentjohnson.hyperble.utils.HyperBleThrowable
import io.reactivex.Observable
import java.util.*


sealed class HyperBleScanner {
    protected val mAdapter: BluetoothAdapter? = getDefaultAdapter()
    protected var mIsScanning: Boolean = false

    abstract fun startScan(): Observable<BluetoothDevice>
    abstract fun stopScan()

    fun isScanning(): Boolean = mIsScanning

    companion object {
        fun getInstance(): HyperBleScanner {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> HyperBleScanner24()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> HyperBleScanner21()
                else -> HyperBleScanner18()
            }
        }
    }

    @RequiresApi(24)
    class HyperBleScanner24 : HyperBleScanner() {
        private lateinit var mBleScanCallback: ScanCallback
        private var mLastStartedScanTime: Long = 0
        private val mScanHandler = Handler()
        private val mScanRunnable = Runnable {
            mLastStartedScanTime = Calendar.getInstance().timeInMillis
            mIsScanning = true
            mAdapter?.bluetoothLeScanner?.startScan(emptyList(), ScanSettings.Builder().build(), mBleScanCallback)
        }

        override fun startScan(): Observable<BluetoothDevice> {
            return Observable.create { subscriber ->
                mScanHandler.removeCallbacks(mScanRunnable)
                if(!isScanning()) {
                    mBleScanCallback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, r: ScanResult?) {
                            r?.let { result ->
                                subscriber.onNext(result.device)
                            }
                        }

                        override fun onScanFailed(errorCode: Int) {
                            mIsScanning = false
                            subscriber.onError(HyperBleThrowable(errorCode, true))
                        }
                    }
                    val timeNeededUntilNextScan = getTimeUntilNextPossibleScan()
                    if (timeNeededUntilNextScan > 0) {
                        mScanHandler.postDelayed(mScanRunnable, timeNeededUntilNextScan)
                    } else {
                        mScanRunnable.run()
                    }
                } else {
                    subscriber.onError(HyperBleThrowable(ScanCallback.SCAN_FAILED_ALREADY_STARTED, true))
                }
            }
        }

        override fun stopScan() {
            mScanHandler.removeCallbacks(mScanRunnable)
            if (isScanning()) {
                mIsScanning = false
                mAdapter?.bluetoothLeScanner?.stopScan(mBleScanCallback)
            }
        }

        private fun getTimeUntilNextPossibleScan(): Long {
            val timeSinceLastScan: Long = Calendar.getInstance().timeInMillis - mLastStartedScanTime
            return if (timeSinceLastScan < 6000) 6000 - timeSinceLastScan else 0
        }
    }

    @RequiresApi(21)
    class HyperBleScanner21 : HyperBleScanner() {

        private lateinit var mBleScanCallback: ScanCallback

        override fun startScan(): Observable<BluetoothDevice> {
            return Observable.create { subscriber ->
                if (!isScanning()) {
                    mIsScanning = true
                    mBleScanCallback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, r: ScanResult?) {
                            r?.let { result ->
                                subscriber.onNext(result.device)
                            }
                        }

                        override fun onScanFailed(errorCode: Int) {
                            mIsScanning = false
                            subscriber.onError(HyperBleThrowable(errorCode, true))
                        }
                    }
                    mAdapter?.bluetoothLeScanner?.startScan(
                        emptyList(),
                        ScanSettings.Builder().build(),
                        mBleScanCallback)
                } else {
                    subscriber.onError(HyperBleThrowable(ScanCallback.SCAN_FAILED_ALREADY_STARTED, true))
                }
            }
        }

        override fun stopScan() {
            if (isScanning()) {
                mIsScanning = false
                mAdapter?.bluetoothLeScanner?.stopScan(mBleScanCallback)
            }
        }
    }

    @Suppress("DEPRECATION")
    class HyperBleScanner18 : HyperBleScanner() {
        private lateinit var mBleScanCallback: LeScanCallback

        override fun startScan(): Observable<BluetoothDevice> {
            return Observable.create { subscriber ->
                if(!isScanning()) {
                    mIsScanning = true
                    mBleScanCallback =
                        LeScanCallback { device, _, _ ->
                            device?.let { d ->
                                subscriber.onNext(d)
                            }
                        }
                    mAdapter?.startLeScan(mBleScanCallback)
                } else {
                    subscriber.onError(HyperBleThrowable(1, true))
                }
            }
        }

        override fun stopScan() {
            mAdapter?.stopLeScan(mBleScanCallback)
        }
    }
}

