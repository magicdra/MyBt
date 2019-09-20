package com.cyberinco.btlibrary;

public class ReceiveResult {

    //接收信息结果
    private int result;

    //接收到的数据
    private byte[] data;

    public ReceiveResult(int result, byte[] data) {
        this.result = result;
        this.data = data;
    }

    public int getResult() {
        return result;
    }

    public byte[] getData() {
        return data;
    }
}
