package com.nissha.android.things.sample.enocean;

import android.content.Context;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * EnOcean Message(packet data) class.
 */

public class EnOceanMessage {

    /**
     * EnOcean Sync. Byte
     */
    public static final byte SYNC_BYTE = 0x55;

    /**
     * PacketType : ERP2
     */
    public static final int PACKET_TYPE_ERP2 = 0x0A;

    public static final int MIN_DATA_LEN = 9;

    private static final int HEADER_SIZE = 4;

    private static final int DATA_OFFSET = 6;

    private byte[] mMessage;

    /**
     * コンストラクタ.
     */
    public EnOceanMessage() {
    }

    /**
     * コンストラクタ.
     *
     * @param data 受信したデータ.
     * @throws Exception 例外.
     */
    public EnOceanMessage(byte[] data) throws Exception {
        if (data == null) {
            throw new Exception("Need data.");
        }

        if (!isEnOceanData(data)) {
            throw new Exception("Data is NOT EnOcean packet...");
        }

        int packetSize = getPacketSize(data);
        if (packetSize == 0) {
            throw new Exception("Data is too short...");
        }

        mMessage = new byte[packetSize];
        System.arraycopy(data, 0, mMessage, 0, packetSize);
    }

    /**
     * Messageからセンサー情報を取得する
     *
     * @param context Context
     * @return モジュール
     */
    public EnOceanModule getEnOceanModule(final Context context) {

        final EEP eep = EEP.getEEP(mMessage);

        final int rssi = getRSSI(mMessage);

        if (eep != null) {
            return eep.analyze(context, rssi);
        }

        return null;
    }

    /**
     * RSSIを取得する.
     *
     * @return RSSI.
     */
    public static int getRSSI(byte[] data) {
        if (data == null) {
            return 0;
        }

        if (data.length < 2) {
            return 0;
        }

        int offset = data.length - 2;

        int rssi = data[offset] & 0xFF;
        rssi = -rssi; // 符号が付いてない値が送信されてくる
        return rssi;
    }

    public static boolean isTargetData(byte[] data) {
        if (data == null) {
            return false;
        }

        if (data.length < (HEADER_SIZE + 1)) {
            return false;
        }

        if (!isEnOceanData(data)) {
            return false;
        }

        int packetType = data[4];
        if (packetType != PACKET_TYPE_ERP2) {
            return false;
        }

        return true;
    }

    private static boolean isEnOceanData(byte[] data) {
        int sync = data[0];
        if (sync == SYNC_BYTE) {
            return true;
        }

        return false;
    }

    /**
     * ESPのパケット全体長を取得する.
     *
     * @param data USBドングルで受信したデータ配列
     * @return パケット長
     */
    public static int getPacketSize(byte[] data) {
        int packetSize = 0;
        if (data == null) {
            return packetSize;
        }

        if (data.length < 5) {
            return packetSize;
        }

        // Headerからデータ長を取得
        int offset = 1;
        int dataLen = getDataLen(data);

        // ESP HeaderからOptional Lengthを取得
        offset += 2;
        int optDataLen = data[offset];

        packetSize = 7 + dataLen + optDataLen; // 7=SyncByte + Header(4byte) + CRC8 Header, CRC8 Data

        return packetSize;
    }

    /**
     * ESP HeaderからERPのデータ長を取得する.
     *
     * @param data ESPのデータ.
     * @return ERPのデータ長
     */
    public static int getDataLen(byte[] data) {
        int dataLen = 0;
        if (data == null) {
            return dataLen;
        }

        if (data.length < 3) {
            return dataLen;
        }

        // ESP Headerからデータ長を取得
        int offset = 1;
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        dataLen = byteBuffer.getShort(offset);

        return dataLen;
    }
}
