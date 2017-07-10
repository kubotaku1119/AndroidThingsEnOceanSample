package com.nissha.android.things.sample.enocean;

/**
 * CO2 sensor module class.
 */

public class CO2Sensor extends EnOceanModule {

    public CO2SensorData mSensorData;

    public CO2Sensor(CO2SensorData sensorData) {
        super.setSensorData(sensorData);
        mSensorData = sensorData;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        int concentration = mSensorData.mConcentration;
        sb.append(concentration)
                .append("ppm");

        double voltage = mSensorData.mVoltage;
        sb.append(" / ")
                .append(voltage)
                .append("V");

        double temperature = mSensorData.mTemperature;
        sb.append(" / ")
                .append(temperature)
                .append("°C");

        double humidity = mSensorData.mHumidity;
        sb.append(" / ")
                .append(humidity)
                .append("%");

        return sb.toString();
    }
}
