package com.nissha.android.things.sample.enocean;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CO2 sensor data class.
 */

public class CO2SensorData extends EnOceanSensorData {

    public String mSensorId;

    /**
     * ガス濃度(ppm).
     */
    public int mConcentration;

    /**
     * 供給電圧(0 - 5.1V).
     */
    public double mVoltage;

    /**
     * 温度(0 - 51℃).
     */
    public double mTemperature;

    /**
     * 湿度(0 - 100%)
     */
    public double mHumidity;

    /**
     * データ受信時刻(Unix Time)
     */
    public long mTime;

    /**
     * RSSI
     */
    public int mRSSI;


    /**
     * コンストラクタ.
     */
    public CO2SensorData(String sensorId) {
        this(new java.util.Date().getTime(), sensorId);
    }

    /**
     * コンストラクタ.
     *
     * @param time データ取得時間.
     */
    public CO2SensorData(long time, String sensorId) {
        mTime = time;
        mSensorId = sensorId;
    }

    @Override
    public float getValues(int index) {
        switch (index) {
            case 0:
                return (float) mConcentration;

            case 1:
                return (float) mTemperature;

            case 2:
                return (float) mHumidity;
        }
        return 0;
    }

    @Override
    public String getXDataLabel() {
        java.util.Date date = new Date(mTime);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(date);
    }
}
