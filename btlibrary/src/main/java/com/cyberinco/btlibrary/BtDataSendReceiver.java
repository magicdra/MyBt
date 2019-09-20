package com.cyberinco.btlibrary;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

class BtDataSendReceiver {

    private BluetoothSocket mSocket;

    //缓冲区
    private byte[] mBuffer;

    //输出流
    private OutputStream mOutputStream;

    //输入流
    private InputStream mInputStream;

    //数据发送管理器
    private Flowable<byte[]> mSendFlowable;

    //发射器
    private FlowableEmitter<byte[]> mSendEmitter;

    //用于请求发送下一条数据
    private Subscription mSendDataSubscription;

    //当前正在发送的数据包
    private byte[] currentSendBytes;

    private static final String TAG = "BtDataSendReceiver";


    //发送消息回调
    private DeviceSendCallback mDeviceSendCallback;

    private DeviceReceiveCallback mDeviceReceiveCallback;

    //接收线程
    private Disposable mReadDisposable;

    //inputStream读取长度
    private int mLength;

    //阅读完成
    private boolean readFinish = true;

    private static final byte BOX_ADDRESS = (byte) 0xC1;

    private static final byte PHONE_ADDRESS = (byte) 0xA1;

    //包长度
    private int packetLength;

    //包数据
    private byte[] packet;

    //已阅读字节数
    private int hasReadLength;

    protected boolean init(BluetoothSocket socket,DeviceSendCallback deviceSendCallback,DeviceReceiveCallback
            deviceReceiveCallback) throws IOException {
        if (socket == null) return false;
        this.mSocket = socket;
        mBuffer = new byte[1024];
        this.mOutputStream = socket.getOutputStream();
        this.mInputStream = socket.getInputStream();
        this.mDeviceSendCallback = deviceSendCallback;
        this.mDeviceReceiveCallback = deviceReceiveCallback;
        initSendDisposable();
        initReadDisposable();
        return true;
    }

    private void initSendDisposable(){
        if (mSendFlowable == null){
            mSendFlowable = Flowable.create(new FlowableOnSubscribe<byte[]>() {
                @Override
                public void subscribe(FlowableEmitter<byte[]> e) throws Exception {
                    mSendEmitter = e;
                }
            }, BackpressureStrategy.ERROR);
            mSendFlowable.subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(new Subscriber<byte[]>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            mSendDataSubscription = s;
                            s.request(1);
                        }

                        @Override
                        public void onNext(byte[] sendBytes) {
                            if (sendBytes == null || sendBytes.length == 0) return;//数据非法
                            if (mSocket == null || !mSocket.isConnected()){//确保socket处于连接状态
                                mSendDataSubscription.request(1);
                                return;
                            }
                            try {
                                if (mOutputStream == null){
                                    mOutputStream = mSocket.getOutputStream();
                                }
                                currentSendBytes = sendBytes;
                                mOutputStream.write(currentSendBytes);
                                mOutputStream.flush();
                                if (mDeviceSendCallback != null){
                                    mDeviceSendCallback.sendData(true);
                                }
                            }catch (IOException e){
                                Log.e(TAG,CrashUtils.getStackTraceInfo(e));
                                if (mDeviceSendCallback != null){
                                    mDeviceSendCallback.sendData(false);
                                }
                            }

                        }

                        @Override
                        public void onError(Throwable t) {
                            Log.e(TAG,CrashUtils.getStackTraceInfo(t));
                            if (mDeviceSendCallback != null){
                                mDeviceSendCallback.sendData(false);
                            }
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        }
    }

    public void reInitSendDisposable(){//重新初始化发送线程
        if (mSendDataSubscription != null){//先取消发送
            mSendDataSubscription.cancel();
        }
        mSendFlowable = null;
        initSendDisposable();
    }

    private void initReadDisposable(){
        if (mReadDisposable == null){
            mReadDisposable = Observable.create(new ObservableOnSubscribe<ReceiveResult>() {
                @Override
                public void subscribe(ObservableEmitter<ReceiveResult> e) throws Exception {
                    while (true){
                        if (mSocket == null){
                            break;
                        }
                        try {
                            if (mSocket != null && mSocket.isConnected()){
                                if (mInputStream == null){
                                    mInputStream = mSocket.getInputStream();
                                }else if (mInputStream.available() != 0){
                                    mLength = mInputStream.read(mBuffer);

//                                    String s1 = ByteString.of(mBuffer,0, mLength).hex();
                                    if (readFinish){
                                        for (int i = 0; i < mLength; i++){
                                            if (mBuffer[i] == 0x05){//读到包头
                                                if (mLength - i < 10){//基础数据长度为10，不满足，直接跳出循环
                                                    break;
                                                }
                                                if (mBuffer[i+3] == BOX_ADDRESS && mBuffer[i+4] == PHONE_ADDRESS){//验证为符合格式的数据
                                                    int properties = HexUtil.twoBytesToInt(new byte[]{mBuffer[i+1],mBuffer[i+2]});
                                                    packetLength = properties & 4095;
                                                    packet = new byte[packetLength+10];
                                                    if (mLength < packet.length){//读出来的长度小于包长度，非法数据
                                                        break;
                                                    }
                                                    if ((mLength - (i+10)) == packetLength){//剩余字节大于包长度
                                                        System.arraycopy(mBuffer,i,packet,0,packet.length);
//                                                        e.onNext(btDataHandler.handleMessage(handleReadData()));
                                                        e.onNext(new ReceiveResult(MyBt.RESULT_RECEIVE_SUCCESS,
                                                                packet));
                                                        //发送下一条
                                                        mSendDataSubscription.request(1);
//                                                        logUtils.i("equal packetLength");
                                                        break;//跳出循环
                                                    }else if ((mLength - (i+10)) > packetLength){
                                                        System.arraycopy(mBuffer,i,packet,0,packet.length);
//                                                        e.onNext(btDataHandler.handleMessage(handleReadData()));
                                                        e.onNext(new ReceiveResult(MyBt.RESULT_RECEIVE_SUCCESS,
                                                                packet));
                                                        //发送下一条
                                                        mSendDataSubscription.request(1);
//                                                        logUtils.i("over packetLength");
                                                        i= i+ 10 + packetLength - 1;//将读取位置向后移
                                                    }else {//小于包长度,先读剩余字节
                                                        System.arraycopy(mBuffer,i,packet,0,hasReadLength = mLength - i);
                                                        readFinish = false;
//                                                        logUtils.i("less than packetLength");
                                                        break;//跳出循环
                                                    }
                                                }
                                            }
                                        }
                                    }else {//上次没有阅读完成
                                        System.arraycopy(mBuffer,0,packet,hasReadLength ,packet.length-hasReadLength);
                                        e.onNext(new ReceiveResult(MyBt.RESULT_RECEIVE_SUCCESS,
                                                packet));
//                                        e.onNext(btDataHandler.handleMessage(handleReadData()));
                                        //发送下一条
                                        mSendDataSubscription.request(1);
                                    }
                                }
                            }
                        }catch (Exception exception){
//                            logUtils.e("蓝牙信息接收失败");
//                            logUtils.e(CrashUtils.getStackTraceInfo(exception));
                            e.onNext(new ReceiveResult(MyBt.RESULT_HANDLE_FAIL,null));
                        }

                    }
                }
            }).subscribeOn(Schedulers.io())
                    .subscribe(new Consumer<ReceiveResult>() {
                        @Override
                        public void accept(ReceiveResult receiveResult) throws Exception {
//                            logUtils.i("handleResult"+success);
//                            if (!success){
//                                handleFailTime++;
//                            }
//                            if (handleFailTime > 60 * 2){//连续一分钟失败
//                                logUtils.i("蓝牙连续失败");
//                                TipsLiveData.getInstance().getTipsListener().updateTips("设备连续通信失败");
//                                handleFailTime = 0;
//                            }
                            mDeviceReceiveCallback.receiveData(receiveResult);
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            //接收消息失败，重连
//                            logUtils.e("蓝牙消息异常"+throwable.getCause()+throwable.getMessage());
//                            if (readDisposableFailTime > 3){//失败次数大于3次
//                                readDisposableFailTime = 0;
//                                reconnectDevice("蓝牙消息异常"+throwable.getCause()+throwable.getMessage());
//                            }else {
//                                readDisposableFailTime++;
//                                mReadDisposable = null;
//                                initReadDisposable();
//                            }
                            mDeviceReceiveCallback.receiveData(new ReceiveResult(MyBt.RESULT_RECEIVE_FAIL,null));
                        }
                    });
        }
    }


    public void reInitReadDisposable(){
        if (mReadDisposable != null){
            mReadDisposable.dispose();
        }
        mReadDisposable = null;
        initReadDisposable();
    }

    public void sendData(byte[] bytes){
        if (mSendEmitter != null){
            mSendEmitter.onNext(bytes);
        }
        mSendDataSubscription.request(1);
    }

    public void destory(){
        try {
            if (mInputStream != null){
                mInputStream.close();
            }
            if (mOutputStream != null){
                mOutputStream.close();
            }
            if (mSocket != null){
                mSocket.close();
            }
            if (mReadDisposable != null){
                mReadDisposable.dispose();
            }
            if (mSendDataSubscription != null){
                mSendDataSubscription.cancel();
            }
        }catch (Exception e){
            Log.e(TAG,CrashUtils.getStackTraceInfo(e));
        }finally {
            mInputStream = null;
            mOutputStream = null;
            mSocket = null;
            mReadDisposable = null;
            mSendDataSubscription = null;
        }
    }
}
