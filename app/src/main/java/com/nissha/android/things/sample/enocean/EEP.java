package com.nissha.android.things.sample.enocean;

import android.content.Context;

import java.util.Locale;

/**
 * EEP
 */

public abstract class EEP {

    /**
     * Minimum packet data length
     */
    public static final int MIN_PACKET_LEN = 14;

    /**
     * Offset to payload data
     */
    private static final int OFFSET_PAYLOAD = 6;

    /**
     * ペイロードデータ.
     */
    protected byte[] mPayloadData;

    /**
     * センダーID.
     */
    protected byte[] mSenderID;

    /**
     * コンストラクタ.
     *
     * @param payloadData ペイロードデータ.
     * @param senderID    センダーID.
     */
    public EEP(byte[] payloadData, byte[] senderID) {
        mPayloadData = new byte[payloadData.length];
        System.arraycopy(payloadData, 0, mPayloadData, 0, payloadData.length);

        mSenderID = new byte[senderID.length];
        System.arraycopy(senderID, 0, mSenderID, 0, senderID.length);
    }

    /**
     * センダーIDを文字列で取得する.
     *
     * @return センダーID文字列.
     */
    public String getSensorID() {
        return getSensorID(mSenderID);
    }

    /**
     * センダーIDを文字列に変換する.
     *
     * @param data センダーIDのbyte配列.
     * @return センダーID文字列.
     */
    public static String getSensorID(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format(Locale.getDefault(), "%02X", b));
        }
        String sensorId = sb.toString();
        sensorId = sensorId.substring(0, sensorId.length());
        return sensorId;
    }

    /**
     * EEPに応じてデータを解析し、EnOceanのデバイス情報を生成する.
     *
     * @param context コンテキスト.
     * @param rssi    RSSI
     * @return 生成したEnOceanデバイス情報.
     */
    public abstract EnOceanModule analyze(Context context, int rssi);


    /**
     * センサーから受信したデータを解析して対象のEEPを取得する.
     *
     * @param data 受信データ.
     * @return EEP.
     */
    public static EEP getEEP(byte[] data) {
        EEP eep = null;

        if ((data == null) || (data.length < MIN_PACKET_LEN)) {
            return null;
        }

        if (data[4] != EnOceanMessage.PACKET_TYPE_ERP2) {
            return null;
        }

        try {
            // ERPのデータ長
            int dataLen = EnOceanMessage.getDataLen(data);

            int offset = OFFSET_PAYLOAD;

            // ERPヘッダー
            int erpHeader = data[offset];

            // 拡張ヘッダー有無
            boolean existExtHeader = existExtHeader(erpHeader);
            int optLen = 0;
            if (existExtHeader(erpHeader)) {
                offset += 1;
            }

            // 拡張テレグラム有無
            boolean existExtTelegram = existExtTelegram(erpHeader);
            if (existExtTelegram) {
                offset += 1;
            }

            offset += 1;
            int originatorIDLen = getOriginatorIDLen(erpHeader);

            // センサーIDは常に4byteとして使用する
            byte[] senderId = new byte[4];
            if (originatorIDLen == 3) {
                // 3byteのときは最初に00が入る
                System.arraycopy(data, offset, senderId, 1, originatorIDLen);
            } else if (originatorIDLen == 6) {
                // 6byteのときは3byte目から利用する
                System.arraycopy(data, (offset + 2), senderId, 0, 4);
            } else {
                // 4byteのときはそのまま利用する
                System.arraycopy(data, offset, senderId, 0, originatorIDLen);
            }
            offset += originatorIDLen;

            int destinationIDLen = getDestinationIDLen(erpHeader);
            offset += destinationIDLen;

            // 実データ長
            int payloadLen = dataLen -
                    (1 + // ERP Header
                            (existExtHeader ? 1 : 0) +  // Ext Header(Option)
                            (existExtTelegram ? 1 : 0) +  // Ext Telegram(Option)
                            originatorIDLen +  // OriginatorID
                            destinationIDLen +  // DestinationID(Option)
                            optLen + // OptionData(Option)
                            1); // CRC8

            if (payloadLen < 0) {
                return null;
            }

            // 実データ
            if (data.length <= (offset + payloadLen)) {
                return null; // データ長が不正
            }
            byte[] payload = new byte[payloadLen];
            System.arraycopy(data, offset, payload, 0, payloadLen);


            // EEPを取得
            eep = getEEP(payload, senderId);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return eep;
    }

    /**
     * センサーIDからEEPを取得する
     *
     * @param payload  EEPのペイロードデータ.
     * @param senderId センダーID.
     * @return EEP.
     */
    public static EEP getEEP(byte[] payload, byte[] senderId) {

        EEP eep = null;

        final String sensorID = getSensorID(senderId);
        if (sensorID.equals("040189B8")) {
            eep = new A50904(payload, senderId);
        }

        return eep;
    }

    /**
     * EEPの拡張ヘッダーが存在するか判別する.
     *
     * @param erpHeader erpヘッダー.
     * @return true : 存在する.
     */
    private static boolean existExtHeader(int erpHeader) {
        boolean existExtHeader = false;

        int extHeaderAvailable = erpHeader >> 4;
        if ((extHeaderAvailable & 0x01) == 0x01) {
            existExtHeader = true;
        }

        return existExtHeader;
    }

    /**
     * EEPの拡張テレグラムが存在するか判別する.
     *
     * @param erpHeader erpヘッダー.
     * @return true : 存在する.
     */
    private static boolean existExtTelegram(int erpHeader) {
        boolean existExtTelegram = false;

        int telegramType = erpHeader & 0x0F;
        if (telegramType == 0x0F) {
            existExtTelegram = true;
        }

        return existExtTelegram;
    }

    /**
     * EEPのデバイスIDの長さを判別する.
     *
     * @param erpHeader erpヘッダー.
     * @return デバイスID長.
     */
    private static int getOriginatorIDLen(int erpHeader) {
        int originatorIDLen;
        int addressCtrl = (erpHeader >> 5) & 0x07;
        switch (addressCtrl) {
            default:
            case 0:
                originatorIDLen = 3;
                break;
            case 1:
            case 2:
                originatorIDLen = 4;
                break;
            case 3:
                originatorIDLen = 6;
                break;
        }

        return originatorIDLen;
    }

    /**
     * EEPのデスティネーションIDの長さを判別する.
     *
     * @param erpHeader erpヘッダー.
     * @return デスティネーションID長.
     */
    private static int getDestinationIDLen(int erpHeader) {
        int destinationIDLen = 0;

        int addressCtrl = (erpHeader >> 5) & 0x07;
        if (addressCtrl == 2) {
            destinationIDLen = 4;
        }

        return destinationIDLen;
    }
}
