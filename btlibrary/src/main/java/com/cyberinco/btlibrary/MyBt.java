package com.cyberinco.btlibrary;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class MyBt {

    private ConnectDeviceCallback mConnectDeviceCallback;

    private SendDataCallback mSendDataCallback;

    private ReceiveDataCallback mReceiveDataCallback;

    private Context mContext;

    //蓝牙连接类
    private ConnectDeviceHelper mConnectDeviceHelper;

    //蓝牙收发类
    private BtDataSendReceiver mBtDataSendReceiver;

    //蓝牙连接是否成功
    private boolean connected;

    //发送失败次数
    private int mSendFailTime;

    //处理失败次数
    private int handleFailTime;

    //读取失败次数
    private int mReadFailTime;

    private static final String TAG = "MyBt";

    //是否检测超时
    private boolean mCheckOverTime;

    //上次发送信息成功时间点
    private long mSendSuccessTime;

    //上次接收信息成功时间点
    private long mReadSuccessTime;

    private Disposable checkOverDisposable;

    //没有接受到消息的次数
    private int mNoReceiveTime;

    //接收消息成功
    public static final int RESULT_RECEIVE_SUCCESS = 0;

    //处理消息失败
    public static final int RESULT_HANDLE_FAIL = 1;

    //接收消息失败
    public static final int RESULT_RECEIVE_FAIL = 2;

    //当前发送的字节数据
    private byte[] mCurrentSendBytes;


    public MyBt(Context context,ConnectDeviceCallback connectDeviceCallback,SendDataCallback sendDataCallback,
                ReceiveDataCallback receiveDataCallback,boolean checkOverTime){
        this.mContext = context;
        this.mConnectDeviceCallback = connectDeviceCallback;
        this.mSendDataCallback = sendDataCallback;
        this.mReceiveDataCallback = receiveDataCallback;
        this.mConnectDeviceHelper = new ConnectDeviceHelper(context);
        this.mBtDataSendReceiver = new BtDataSendReceiver();
        this.mCheckOverTime = checkOverTime;
    }



    /**
     * 连接蓝牙设备
     * @param macs 蓝牙设备地址
     */
    public void connectDevice(Set<String> macs){
        mConnectDeviceHelper.connectDevice(macs, new DeviceConnectCallBack() {
            @Override
            public void connectDevice(boolean success, BluetoothSocket bluetoothSocket) {
                if (success && bluetoothSocket != null){//传递过来的蓝牙socket不为空，说明蓝牙连接成功
                    try {
                        if (mBtDataSendReceiver.init(bluetoothSocket, mDeviceSendCallback, mDeviceReceiveCallback)){//初始化收发线程
                            //成功，通知外部成功
                            connected = true;
                            mConnectDeviceCallback.connectDevice(true);

                        }else {
                            connected = false;
                            mConnectDeviceCallback.connectDevice(false);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        connected = false;
                        mConnectDeviceCallback.connectDevice(false);
                    }
                }else {
                    connected = false;
                }
            }
        });
    }

    /**
     * 发送数据回调
     * @param data 需要发送的数据
     */
    public void sendData(byte[] data){
        if (connected){
            mCurrentSendBytes = data;//记录现在发送的数据
            mBtDataSendReceiver.sendData(data);
            if (mCheckOverTime){
                checkSendDataOverTime();
            }
        }else {
            mSendDataCallback.sendData(false);
        }
    }


    private void checkSendDataOverTime(){
        if(checkOverDisposable == null){
            checkOverDisposable = Observable.interval(5,5, TimeUnit.SECONDS)
                    .subscribe(new Consumer<Long>() {
                        @Override
                        public void accept(Long aLong) throws Exception {
                            if (connected && System.currentTimeMillis() - mReadSuccessTime > 5000) {
                                //没有处理成功，并且距离上次处理成功时间超过3S
                                if (mNoReceiveTime > 3) {
                                    //触发重连
                                    Log.i(TAG,"触发重连");
                                    mConnectDeviceHelper.reconnectDevice(true);
                                } else {
                                    //重新发送数据
                                    mBtDataSendReceiver.sendData(mCurrentSendBytes);
                                    mNoReceiveTime++;
                                }
                            }
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            if (mNoReceiveTime > 3){
                                //触发重连
                                mConnectDeviceHelper.reconnectDevice(true);
                            }else {
                                checkOverDisposable = null;
                                checkSendDataOverTime();
                            }
                        }
                    });
        }
    }

    private DeviceSendCallback mDeviceSendCallback = new DeviceSendCallback() {
        @Override
        public void sendData(boolean success) {
            if (!success){
                if (mSendFailTime > 3){//失败次数大于3次,reconnect
                    mSendFailTime = 0;
                    try {
                        mConnectDeviceHelper.reconnectDevice(true);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG,CrashUtils.getStackTraceInfo(e));
                    }
                }else {
                    mSendFailTime++;
                    mBtDataSendReceiver.reInitSendDisposable();
                }
            }
            mSendDataCallback.sendData(success);
        }
    };

    private DeviceReceiveCallback mDeviceReceiveCallback = new DeviceReceiveCallback() {
        @Override
        public void receiveData(ReceiveResult receiveResult) {
            if (receiveResult.getResult() == RESULT_RECEIVE_SUCCESS){
                mReadSuccessTime = System.currentTimeMillis();
                Log.i("receive",Hex.encodeHexStr(receiveResult.getData()));
                mReceiveDataCallback.receiveData(RESULT_RECEIVE_SUCCESS,receiveResult.getData());
            }else if (receiveResult.getResult() == RESULT_HANDLE_FAIL){
                handleFailTime++;
                if (handleFailTime > 60 * 2){//连续一分钟失败
                    handleFailTime = 0;
                    mReceiveDataCallback.receiveData(RESULT_HANDLE_FAIL,null);
                }
            }else if (receiveResult.getResult() == RESULT_RECEIVE_FAIL){
                if (mReadFailTime > 3){//失败次数大于3次
                    mReadFailTime = 0;
                    try {
                        mConnectDeviceHelper.reconnectDevice(true);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG,CrashUtils.getStackTraceInfo(e));
                    }
                }else {
                    mReadFailTime++;
                    mBtDataSendReceiver.reInitReadDisposable();
                }
            }
        }
    };

    public void onDestory(){
        if (mBtDataSendReceiver != null){
            mBtDataSendReceiver.destory();
        }
        if (mConnectDeviceHelper != null){
            mConnectDeviceHelper.destory();
        }
    }

}
