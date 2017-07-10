package com.nissha.android.things.sample;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.nissha.android.things.sample.enocean.EnOceanMessage;
import com.nissha.android.things.sample.enocean.EnOceanModule;
import com.nissha.android.things.sample.enocean.EnOceanSensorData;
import com.nissha.android.things.sample.usb.USBManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lecho.lib.hellocharts.formatter.SimpleAxisValueFormatter;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;

public class MainActivity extends Activity implements USBManager.IUSBDataListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private USBManager mUSBManager;

    private TextView mTextView;

    private LineChartFragment mLineChartFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
        setContentView(R.layout.activity_main2);

        mUSBManager = new USBManager(this);
        mUSBManager.setListener(this);

        mHandler = new Handler(Looper.getMainLooper());

//        mTextView = (TextView) findViewById(R.id.text_sensor_data);

        // グラフ表示用のレイアウト（Fragment）を生成して配置
        mLineChartFragment = new LineChartFragment();
        getFragmentManager().beginTransaction().add(R.id.view_holder, mLineChartFragment).commit();
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        mUSBManager.openDevice();
    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(mUsbReceiver);
    }

    // --------------------------------

    private Handler mHandler;

    private List<EnOceanSensorData> mSensorDataList = new ArrayList<>();

    @Override
    public void onReceivedData(byte[] data) {
        // データ受信したのでセンサーデータのパースする

        try {

            final EnOceanMessage enOceanMessage = new EnOceanMessage(data);

            final EnOceanModule enOceanModule = enOceanMessage.getEnOceanModule(this);

            if (enOceanModule != null) {

                final String sensorData = enOceanModule.toString();

                Log.d(TAG, sensorData);

                mSensorDataList.add(enOceanModule.getSensorData());

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
//                        final String oldText = mTextView.getText().toString();
//                        String newText = oldText + sensorData + "\n";
//                        mTextView.setText(newText);

                        // 受信したデータリストを渡してグラフを更新
                        mLineChartFragment.setData(mSensorDataList);
                    }
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // --------------------------------

    /**
     * グラフ表示用Fragment
     */
    public static class LineChartFragment extends Fragment {

        private static int[] LINE_COLORS = {
                Color.CYAN,
                Color.YELLOW,
                Color.GREEN,
                Color.MAGENTA,
                Color.BLACK
        };

        private LineChartView mLineChartView;

        private float mMinValue;

        private float mMaxValue;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_linechart, container, false);
        }

        @Override
        public void onStart() {
            super.onStart();

            final View view = getView();
            if (view != null) {
                mLineChartView = (LineChartView) view.findViewById(R.id.chart_line_view);
            }
        }

        private void setViewPort(final long top, final long bottom, final long first, final long last) {
            Viewport viewport = new Viewport(mLineChartView.getMaximumViewport());

            // Viewの最大領域
            viewport.bottom = bottom;
            viewport.top = top;
            viewport.left = 0;
            viewport.right = last;
            mLineChartView.setMaximumViewport(viewport);

            // カレントの表示領域
            viewport.left = first;
            mLineChartView.setCurrentViewport(viewport);
        }

        public void setData(List<EnOceanSensorData> dataList) {
            // 線グラフ用データ生成
            final LineChartData lineChartData = createLineChartData(dataList);

            // グラフにデータセット
            mLineChartView.setLineChartData(lineChartData);

            // 表示領域設定
            int dataNum = dataList.size();
            long first = 0;
            long last = dataNum - 1;
            if (dataNum > 10) {
                // カレントの表示エリアは10件に絞る
                first = (last - 10);
            }

            int top = (int) (mMaxValue + 1);
            int bottom = (int) (mMinValue - 1);

            int diff = (top - bottom);
            if (diff <= 0) {
                top += (Math.abs(diff) + 5);
            }

            setViewPort(top, bottom, first, last);
        }

        private LineChartData createLineChartData(List<EnOceanSensorData> dataList) {
            if (dataList == null) {
                return null;
            }

            if (dataList.size() == 0) {
                return null;
            }

            int dataNum = dataList.size();
            int axisNum = 3; // CO2濃度、温度、湿度の3軸

            Map<Integer, List<PointValue>> valueMap = new HashMap<>();
            for (int axisIndex = 0; axisIndex < axisNum; axisIndex++) {
                valueMap.put(axisIndex, new ArrayList<PointValue>());
            }

            List<AxisValue> axisValues = new ArrayList<>();

            // 異なるレンジのデータを表示する場合(CO2濃度と温度とか)の
            // データのスケーリング値
            int maxValue = 2550; // CO2の濃度最大値
            int maxValue2 = 100; // 温度・湿度の最大値
            int minValue = 0;
            int minValue2 = 0;
            float scale = (maxValue - minValue) / maxValue2;
            float sub = (minValue2 * scale) / 2;

            // データをセットする
            for (int index = 0; index < dataNum; index++) {
                EnOceanSensorData data = dataList.get(index);

                for (int axisIndex = 0; axisIndex < axisNum; axisIndex++) {

                    float orgVal = (float) data.getValues(axisIndex);
                    float v = orgVal;
                    if ((axisIndex != 0) && (scale != 1)) {
                        v = (orgVal * scale) - sub;
                    }


                    if (mMaxValue < v) {
                        mMaxValue = v;
                    }
                    if (mMinValue > v) {
                        mMinValue = v;
                    }

                    PointValue val = new PointValue(index, v);
                    // 表示用ラベルをセット
                    val.setLabel("" + orgVal);
                    valueMap.get(axisIndex).add(val);
                }

                // X軸データに時間文字列を追加
                final String dateLabel = data.getXDataLabel();
                axisValues.add(new AxisValue(index).setLabel(dateLabel));
            }

            List<Line> lines = new ArrayList<>();

            // データライン設定
            for (int axisIndex = 0; axisIndex < axisNum; axisIndex++) {
                List<PointValue> values = valueMap.get(axisIndex);
                Line line = new Line(values);

                int color = Color.BLACK;
                if (axisIndex < LINE_COLORS.length) {
                    color = LINE_COLORS[axisIndex];
                }
                line.setHasLabels(true);
                line.setColor(color);
                line.setPointRadius(0);
                lines.add(line);
            }

            LineChartData data = new LineChartData(lines);

            // X軸設定
            Axis axisX = new Axis(axisValues);
            axisX.setName("時間");
            axisX.setTextColor(Color.BLACK);
            data.setAxisXBottom(axisX);

            // Y軸設定
            int axisYColor = Color.BLACK;
            if (scale != 1) {
                axisYColor = LINE_COLORS[0];
            }
            Axis axisY = new Axis().setHasLines(true).setName("データ")
                    .setHasTiltedLabels(false).setTextColor(axisYColor);
            data.setAxisYLeft(axisY);

            // Y軸設定2
            if (scale != 1) {
                // 異なるレンジのデータを表示する際の右側のY軸情報
                data.setAxisYRight(new Axis().setFormatter(new HeightValueFormatter(scale, sub, 0)));
            }

            data.setBaseValue(0);

            return data;
        }

        private static class HeightValueFormatter extends SimpleAxisValueFormatter {
            private float scale;
            private float sub;
            private int decimalDigits;

            HeightValueFormatter(float scale, float sub, int decimalDigits) {
                this.scale = scale;
                this.sub = sub;
                this.decimalDigits = decimalDigits;
            }

            @Override
            public int formatValueForAutoGeneratedAxis(char[] formattedValue, float value, int autoDecimalDigits) {
                float scaledValue = (value + sub) / scale;
                return super.formatValueForAutoGeneratedAxis(formattedValue, scaledValue, this.decimalDigits);
            }
        }
    }

    // --------------------------------

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Toast.makeText(MainActivity.this, "Catch USB Receiver", Toast.LENGTH_SHORT).show();

            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {

                mUSBManager.openDevice();

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

                mUSBManager.closeDevice();

            }
        }
    };

}
