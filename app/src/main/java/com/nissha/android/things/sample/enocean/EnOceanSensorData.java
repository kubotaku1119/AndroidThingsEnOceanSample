package com.nissha.android.things.sample.enocean;

/**
 * Sensor Data common abstract class.
 */

public abstract class EnOceanSensorData {

    public abstract float getValues(final int index);

    public abstract String getXDataLabel();
}
