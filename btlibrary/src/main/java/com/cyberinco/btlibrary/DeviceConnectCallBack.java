package com.cyberinco.btlibrary;

import android.bluetooth.BluetoothSocket;

interface DeviceConnectCallBack {

    /**
     * 连接设备回调
     * @param success 是否成功
     * @param bluetoothSocket 蓝牙socket实例
     */
    void connectDevice(boolean success,BluetoothSocket bluetoothSocket);
}
