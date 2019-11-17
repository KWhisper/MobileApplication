package com.calypso.pedometer.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.calypso.pedometer.R;
import com.calypso.pedometer.constant.Constant;
import com.calypso.pedometer.constant.Preferences;
import com.calypso.pedometer.greendao.DBHelper;
import com.calypso.pedometer.greendao.entry.StepInfo;
import com.calypso.pedometer.stepdetector.StepService;
import com.calypso.pedometer.utils.ConversionUtil;
import com.calypso.pedometer.utils.DateUtil;

import java.text.DecimalFormat;

import static android.content.Context.BIND_AUTO_CREATE;


public class StepPedometer extends Fragment {
    private TextView textView0, textView1, textView2;

    private DecimalFormat df1 = new DecimalFormat("0.00");
    private int stepBenchmark = 0;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.step_pedometer, container, false);

        textView0 = (TextView) view.findViewById(R.id.stepcount);
        textView1 = (TextView) view.findViewById(R.id.step);
        textView2 = (TextView) view.findViewById(R.id.step2);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initBarChatData();
    }

    public void initBarChatData() {
        StepInfo stepInfo = DBHelper.getStepInfo(DateUtil.getTodayDate());
        if (stepInfo != null) {
            long step = stepInfo.getStepCount();
            String mileages = String.valueOf(ConversionUtil.step2Mileage(step, stepBenchmark));
            String calorie = df1.format(ConversionUtil.step2Calories(step, stepBenchmark));
            textView0.setText("   已走:" + step + "步");
            textView1.setText("   卡路里:" + calorie + "卡");
            textView2.setText("  行走:" + mileages + "米");
        }
    }

    public boolean handleMessage(Message msg) {

        long step = msg.getData().getLong("step");
        String mileages = String.valueOf(ConversionUtil.step2Mileage(step, stepBenchmark));
        String calorie = df1.format(ConversionUtil.step2Calories(step, stepBenchmark));
        textView1.setText("卡路里:" + calorie + "卡");
        textView2.setText("  行走:" + mileages + "米");

        return true;
    }
}