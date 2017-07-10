package com.nissha.android.things.sample.enocean;

/**
 * EnOcean module common abstract class.
 */

public abstract class EnOceanModule {

    private EnOceanSensorData mSensorData;

    public EnOceanSensorData getSensorData() {
        return mSensorData;
    }

    public void setSensorData(EnOceanSensorData sensorData) {
        mSensorData = sensorData;
    }
}
