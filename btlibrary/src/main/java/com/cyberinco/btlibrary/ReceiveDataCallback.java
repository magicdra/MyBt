package com.cyberinco.btlibrary;

public interface ReceiveDataCallback {

    /**
     * 接收消息回调
     * @param result 接收到的结果
     * @param data 接收到的蓝牙消息
     */
    void receiveData(int result,byte[] data);
}
