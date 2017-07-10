package com.nissha.android.things.sample.enocean;

import android.content.Context;

/**
 * EEP - A5-09-04
 */

public class A50904 extends EEP {

    /**
     * コンストラクタ.
     *
     * @param payloadData EEPデータ.
     * @param senderID    デバイスID.
     */
    public A50904(byte[] payloadData, byte[] senderID) {
        super(payloadData, senderID);
    }

    @Override
    public EnOceanModule analyze(Context context, int rssi) {
        byte[] data = mPayloadData;

        String sensorId = super.getSensorID();

        CO2SensorData sensorData = new CO2SensorData(sensorId);

        // 湿度
        int humData = data[0] & 0xFF;
        sensorData.mHumidity = calcHumidity(humData);

        // 濃度
        int contData = data[1] & 0xFF;
        sensorData.mConcentration = calcConcentration(contData);

        // 温度
        int tempData = data[2] & 0xFF;
        sensorData.mTemperature = calcTemperature(tempData);

        // RSSI
        sensorData.mRSSI = rssi;

        return new CO2Sensor(sensorData);
    }

    private double calcHumidity(int data) {
        return data * 0.5;
    }

    private int calcConcentration(int data) {
        return (data * 10);
    }

    private double calcTemperature(int data) {
        double dt = (double) 51 / 255;
        double temp = (dt * data);
        return roundValue(temp);
    }

    private double roundValue(double val) {
        double tempVal = val * 10;
        tempVal = Math.round(tempVal);
        return (tempVal / 10);
    }

}
