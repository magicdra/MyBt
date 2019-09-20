package com.cyberinco.btlibrary;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

class ConnectDeviceHelper {

    private DeviceConnectCallBack mDeviceConnectCallBack;

    //发现线程
    private Disposable mDiscoveryDisposable;

    private Set<String> mMacs;

    //蓝牙适配器
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    //用于连接的蓝牙设备
    private BluetoothDevice mBluetoothDevice;

    private static final String TAG = "ConnectDeviceHelper";

    //当前蓝牙是否连接
    private boolean connected;

    //当前是否为正在连接状态
    private boolean connecting;

    //蓝牙socket
    private BluetoothSocket bluetoothSocket;

    //连接线程
    private Disposable connectDisposable;

    private Context mContext;

    //发现成功时间
    private long discoverySuccessTime;

    //关闭蓝牙适配器的时间
    private long closeBtAdapterTime;

    private boolean firstStart = true;

    ConnectDeviceHelper(Context context){
        this.mContext = context;
        init();
    }

    /**
     * 初始化连接类
     */
    private void init(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);

        mContext.registerReceiver(receiver,intentFilter);
    }

    protected void connectDevice(Set<String> macs, DeviceConnectCallBack deviceConnectCallBack){

        this.mMacs = macs;

        this.mDeviceConnectCallBack = deviceConnectCallBack;

        findDevice();
    }


    //与蓝牙盒子建立关联
    private void findDevice(){

        if (mDiscoveryDisposable == null){
            mDiscoveryDisposable = Observable.interval(0,20,TimeUnit.SECONDS).
                    observeOn(Schedulers.io())
                    .subscribe(new Consumer<Long>() {
                        @Override
                        public void accept(Long l) throws Exception {
                            if (mMacs == null || mMacs.size() == 0){
                                return;
                            }
                            if (connected){//当前是蓝牙连接状态，不处理
                                return;
                            }
                            if (!firstStart && System.currentTimeMillis() - discoverySuccessTime > 2 * 60 * 1000){
                                closeBtAdapter();
                            }
                            if (!mBluetoothAdapter.isEnabled()){//发现蓝牙关闭，打开蓝牙
                                mBluetoothAdapter.enable();
                            }
                            //先获取已经关联过的蓝牙设备
                            Set<BluetoothDevice> bluetoothDevices = mBluetoothAdapter.getBondedDevices();
                            if (bluetoothDevices == null || bluetoothDevices.size() == 0){
                                discoveryDevices();
                            }else {
                                Iterator<BluetoothDevice> iterator = bluetoothDevices.iterator();
                                while (iterator.hasNext()){
                                    BluetoothDevice device =iterator.next();
                                    if (mMacs.contains(device.getAddress())){
                                        connected = false;
                                        mBluetoothDevice = device;
                                        connectDevice();
                                        break;
                                    }
                                }
                                //已绑定设备中不包含需绑定的设备
                                discoveryDevices();
                            }
                        }
                    });
        }
    }

    //发现周围设备
    private void discoveryDevices(){
        Log.i(TAG,"开始发现蓝牙");
        //没有发现到蓝牙设备,蓝牙连接失败
        if (!mBluetoothAdapter.isDiscovering()) {
            //当前不处于发现状态，并且未连接
            while (!mBluetoothAdapter.startDiscovery()){
                mBluetoothAdapter.startDiscovery();
            }
        }
    }


    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()){
                //找到蓝牙
                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice bluetoothDevice = intent
                            .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String macAddress = bluetoothDevice.getAddress();
                    Log.i(TAG,"发现"+macAddress);
                    discoverySuccessTime = System.currentTimeMillis();//记录发现成功时间
                    //获取服务器绑定的设备Mac地址
                    if (mMacs.contains(macAddress)) {
                        mBluetoothDevice = bluetoothDevice;
                        mBluetoothAdapter.cancelDiscovery();
                        if (!connected){
                            connectDevice();
                        }
                    }
                    break;
                //蓝牙断开连接
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                    Log.e(TAG,"设备断开连接");
                    connected = false;
                    connecting = false;
                    try {
                        if (bluetoothSocket != null){
                            bluetoothSocket.close();
                            bluetoothSocket = null;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (connectDisposable != null){
                        connectDisposable.dispose();
                        connectDisposable = null;
                    }
                    mDeviceConnectCallBack.connectDevice(false,null);
                    findDevice();
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    Log.i(TAG,"停止发现");
                    if (!connected){
                        discoveryDevices();
                    }
                    break;
                case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    abortBroadcast();//截断广播
                    try {
                        ClsUtils.setPin(mBluetoothDevice.getClass(), mBluetoothDevice, "0000");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    };

    private void connectDevice(){
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (connectDisposable == null){
            connectDisposable= Observable.create(new ObservableOnSubscribe<BluetoothSocket>() {
                @Override
                public void subscribe(ObservableEmitter<BluetoothSocket> e) throws Exception {
                    try{
                        Method m = mBluetoothDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
                        bluetoothSocket = (BluetoothSocket) m.invoke(mBluetoothDevice, 1);
//                    bluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                        //连接成功
                        if (!connecting){
                            connecting = true;
                            Log.i("connect","startConnect:"+System.currentTimeMillis());
                                bluetoothSocket.connect();

                            Log.i("connect","connectFinish:"+System.currentTimeMillis());
                            e.onNext(bluetoothSocket);
                        }
                    }catch (IOException exception){
                        e.onError(exception);
                    }
                    e.onComplete();
                }
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<BluetoothSocket>() {
                        @Override
                        public void accept(BluetoothSocket bluetoothSocket) throws Exception {
                            connecting = false;
                            if (bluetoothSocket != null) {
                                connected = true;
                                mBluetoothAdapter.cancelDiscovery();//停止发现
                                mDeviceConnectCallBack.connectDevice(true,bluetoothSocket);
                            } else {
                                connected = false;
                                reconnectDevice(false);
                            }
                        }

                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            connecting = false;
                            Log.e(TAG,CrashUtils.getStackTraceInfo(throwable));
                            reconnectDevice(false);
                        }
                    }, new Action() {
                        @Override
                        public void run() throws Exception {

                        }
                    });
        }
    }

    /**
     * 重连蓝牙设备
     * @param disableAdapter 是否需要关闭蓝牙适配器
     * @throws IOException
     */
    protected void reconnectDevice(boolean disableAdapter) throws IOException {
        if (!connected) return;//已经处于未连接状态，不需要重连
        if (connectDisposable != null){
            connectDisposable.dispose();
            connectDisposable = null;
        }
        if (bluetoothSocket != null){
            bluetoothSocket.close();
        }
        if (disableAdapter){
            closeBtAdapter();
        }
        if (!mBluetoothAdapter.isDiscovering()){
            findDevice();
        }
    }

    /**
     * 关闭蓝牙适配器，用于重启
     */
    private void closeBtAdapter(){
        if (System.currentTimeMillis() - closeBtAdapterTime > 2 * 60 *1000 &&
                mBluetoothAdapter.isEnabled()){//超过规定时间，才去关闭
            mBluetoothAdapter.disable();
            closeBtAdapterTime = System.currentTimeMillis();//记录关闭适配器的时间
        }
    }


    protected void destory(){
        mContext.unregisterReceiver(receiver);
    }
}
