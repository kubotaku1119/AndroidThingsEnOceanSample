package com.nissha.android.things.sample.usb;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;
import com.nissha.android.things.sample.enocean.EnOceanMessage;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * USB Accessory Management class.
 */

public class USBManager {

    /**
     *
     */
    public interface IUSBDataListener {

        void onReceivedData(byte[] data);
    }

    /**
     * Log用TAG.
     */
    private static final String TAG = USBManager.class.getSimpleName();

    private Context mContext;

    private D2xxManager mInstance;

    private FT_Device mFTDevice;

    private boolean mIsRunning = false;

    private IUSBDataListener mIUSBDataListener;

    public USBManager(Context context) {
        mContext = context;

        try {
            mInstance = D2xxManager.getInstance(context);
        } catch (D2xxManager.D2xxException e) {
            e.printStackTrace();
        }
    }

    public void setListener(IUSBDataListener listener) {
        mIUSBDataListener = listener;
    }

    /**
     * USBデバイスと接続
     *
     * @return 成否
     */
    public boolean openDevice() {
        if (mFTDevice != null) {
            if (mFTDevice.isOpen()) {
                if (!mIsRunning) {
                    setConfig();
                    mIsRunning = true;
                    new Thread(mReadRunner).start();
                }
                return true;
            }
        }

        Log.i(TAG, "+++ openDevice() +++");

        int devCount = mInstance.createDeviceInfoList(mContext);

        Log.i(TAG, "device count : " + devCount);

        Toast.makeText(mContext, "device count" + devCount, Toast.LENGTH_SHORT).show();

        if (devCount > 0) {
            D2xxManager.FtDeviceInfoListNode deviceList = mInstance.getDeviceInfoListDetail(0);

            if (mFTDevice == null) {
                mFTDevice = mInstance.openByIndex(mContext, 0);
            } else {
                synchronized (mFTDevice) {
                    mFTDevice = mInstance.openByIndex(mContext, 0);
                }
            }

            if (mFTDevice.isOpen()) {
                Toast.makeText(mContext, "Succeeded Open Device!!", Toast.LENGTH_SHORT).show();

                if (!mIsRunning) {
                    setConfig();
                    mIsRunning = true;
                    new Thread(mReadRunner).start();
                }
                return true;
            } else {
                Toast.makeText(mContext, "Failed Open Device...", Toast.LENGTH_SHORT).show();
                return false;
            }
        } else {
            // error...
            return false;
        }
    }

    /**
     * USBデバイスを切断
     */
    public void closeDevice() {
        mIsRunning = false;
        if (mFTDevice != null) {
            mFTDevice.close();
        }
    }

    private void setConfig() {
        if ((mFTDevice == null) || (!mFTDevice.isOpen())) {
            return;
        }

        mFTDevice.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);
        mFTDevice.setBaudRate(57600);
    }

    private Runnable mReadRunner = new Runnable() {

        private byte[] receivedData = new byte[4096 * 10];

        private byte[] buf = new byte[4096 * 2];

        private ExecutorService pool = Executors.newCachedThreadPool();

        @Override
        public void run() {

            int receivedSize = 0;

            while (mIsRunning) {
                synchronized (mFTDevice) {
                    int readSize = mFTDevice.getQueueStatus();

                    // CPU負荷低減
                    if (readSize == 0) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (readSize > 0) {
                        // 受信データを読み込む
                        Arrays.fill(buf, (byte) 0x00);
                        if (readSize > buf.length) {
                            readSize = buf.length;
                        }
                        mFTDevice.read(buf, readSize);


                        // 前回読み込んだ途中のデータの後ろにコピーする
                        // （読み込み済みデータがなければ、receivedDataの先頭にコピーされる）
                        System.arraycopy(buf, 0, receivedData, receivedSize, readSize);
                        receivedSize += readSize;

                        // 受信したデータをパケット単位に切り出して通知する
                        while (receivedSize >= EnOceanMessage.MIN_DATA_LEN) {

                            int firstPos = findFirstPos(receivedSize);
                            if (firstPos > 0) {
                                // 先頭がSync Byteでないので、Sync Byteまで移動
                                receivedSize -= firstPos;
                                System.arraycopy(receivedData, firstPos, receivedData, 0, receivedSize);
                            } else if (firstPos == -1) {
                                // Sync Byteが存在しないので再読み込み
                                receivedSize = 0;
                                break;
                            }

                            // 対象データでなければ次のデータを取得する
                            if (!EnOceanMessage.isTargetData(receivedData)) {
                                // EnOceanデータではないので再読み込み
                                receivedSize = 0;
                                break;
                            }

                            // データからパケットサイズを取得する
                            int packetSize = EnOceanMessage.getPacketSize(receivedData);
                            if (packetSize <= 0) {
                                // Packet Sizeが異常なので再読み込み
                                receivedSize = 0;
                                break;
                            }


                            // 取得データがパケットサイズより大きければ１パケットずつに分割
                            if (receivedSize >= packetSize) {

                                // 1パケット分をコピー
                                final byte[] packet = new byte[packetSize];
                                System.arraycopy(receivedData, 0, packet, 0, packetSize);

                                // 通知
                                pool.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mIUSBDataListener != null) {
                                            mIUSBDataListener.onReceivedData(packet);
                                        }
                                    }
                                });

                                // 通知した分をつめる
                                receivedSize -= packetSize;
                                System.arraycopy(receivedData, packetSize, receivedData, 0, receivedSize);
                            } else {
                                // 1パケットに足りないので再読み込みする
                                break;
                            }
                        }
                    }
                }
            }
        }

        private int findFirstPos(int maxSize) {

            int pos = -1;
            for (int index = 0; index < maxSize; index++) {
                byte b = receivedData[index];
                if (b == EnOceanMessage.SYNC_BYTE) {
                    pos = index;
                    break;
                }

            }
            return pos;
        }
    };

}
