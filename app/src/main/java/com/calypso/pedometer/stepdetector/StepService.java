package com.calypso.pedometer.stepdetector;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.calypso.pedometer.constant.Preferences;
import com.calypso.pedometer.constant.Constant;
import com.calypso.pedometer.activity.MainActivity;
import com.calypso.pedometer.R;
import com.calypso.pedometer.greendao.DBHelper;
import com.calypso.pedometer.greendao.entry.StepInfo;
import com.calypso.pedometer.utils.DateUtil;
import com.calypso.pedometer.utils.Timber;

import java.util.Calendar;


@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class StepService extends Service implements SensorEventListener {
    private final String TAG = "StepService";
    private static int duration = 30000;
    private SensorManager sensorManager;
    private StepDetector stepDetector;
    private NotificationManager nm;
    private NotificationCompat.Builder builder;
    private Messenger messenger = new Messenger(new MessengerHandler());
    private BroadcastReceiver mBatInfoReceiver;
    private PowerManager.WakeLock mWakeLock;
    //计步传感器类型 0-counter 1-detector 2-加速度传感器
    private static int stepSensor = -1;
    private static String CURRENTDATE = "";
    private boolean isNewDay = false;
    private long previousStep;
    private long stepTotal;
    private TimeCount time;

    private static class MessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constant.MSG_FROM_CLIENT:
                    try {
                        Messenger messenger = msg.replyTo;
                        Message replyMsg = Message.obtain(null, Constant.MSG_FROM_SERVER);
                        Bundle bundle = new Bundle();
                        bundle.putLong("step", StepDetector.CURRENT_STEP);
                        replyMsg.setData(bundle);
                        messenger.send(replyMsg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.tag("StepService");
        initBroadcastReceiver();
        new Thread(new Runnable() {
            @Override
            public void run() {
                initTodayData();
                startStepDetector();
            }
        }).start();
        startTimeCount();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        save();
        stopForeground(true);
        unregisterReceiver(mBatInfoReceiver);
        Intent intent = new Intent(this, StepService.class);
        startService(intent);
        super.onDestroy();
        if (time != null) {
            time.cancel();
        }
    }

    /**
     * 初始化广播
     */
    private void initBroadcastReceiver() {
        //定义意图过滤器
        final IntentFilter filter = new IntentFilter();
        //屏幕灭屏广播
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        //日期修改
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        //关闭广播
        filter.addAction(Intent.ACTION_SHUTDOWN);
        //屏幕高亮广播
        filter.addAction(Intent.ACTION_SCREEN_ON);
        //屏幕解锁广播
        filter.addAction(Intent.ACTION_USER_PRESENT);
        //当长按电源键弹出“关机”对话或者锁屏时系统会发出这个广播
        //example：有时候会用到系统对话框，权限可能很高，会覆盖在锁屏界面或者“关机”对话框之上，
        //所以监听这个广播，当收到时就隐藏自己的对话，如点击pad右下角部分弹出的对话框
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        mBatInfoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    Log.v(TAG, "screen on");
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.v(TAG, "screen off");
                    save();
                    //改为60秒一存储
                    duration = 60000;
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    Log.v(TAG, "screen unlock");
                    save();
                    //改为30秒一存储
                    duration = 30000;
                } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                    Log.v(TAG, "receive Intent.ACTION_CLOSE_SYSTEM_DIALOGS  出现系统对话框");
                    //保存一次
                    save();
                } else if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
                    Log.v(TAG, "receive ACTION_SHUTDOWN");
                    save();
                } else if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction())) {
                    Log.v(TAG, "receive ACTION_TIME_CHANGED");
                    initTodayData();
                }
            }
        };
        registerReceiver(mBatInfoReceiver, filter);
    }

    /**
     * 初始化当天的日期
     */
    private void initTodayData() {
        CURRENTDATE = DateUtil.getTodayDate();
        StepInfo stepInfo = DBHelper.getStepInfo(CURRENTDATE);
        if (stepInfo == null) {
            StepDetector.CURRENT_STEP = 0;
            isNewDay = true;
            stepInfo = new StepInfo();
            stepInfo.setDate(CURRENTDATE);
            stepInfo.setCreteTime(DateUtil.getTodayTime(DateUtil.DATE_FULL_STR));
            DBHelper.insertStepInfo(stepInfo);
        } else {
            isNewDay = false;
            StepDetector.CURRENT_STEP = stepInfo.getStepCount();
        }
    }

    private void startTimeCount() {
        time = new TimeCount(duration, 1000);
        time.start();
    }


    private void startStepDetector() {
        if (sensorManager != null && stepDetector != null) {
            sensorManager.unregisterListener(stepDetector);
            sensorManager = null;
            stepDetector = null;
        }
        //得到休眠锁，目的是为了当手机黑屏后仍然保持CPU运行，使得服务能持续运行
        getLock(this);
        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        //android4.4以后可以使用计步传感器
        int VERSION_CODES = Build.VERSION.SDK_INT;
        if (VERSION_CODES >= 19) {
            addCountStepListener();
        } else {
            addBasePedoListener();
        }
    }

    /**
     * 使用自带的计步传感器
     * <p>
     * 说明：手机自带计步传感器存储的步数信息是以一个月为周期进行清算
     * <p>
     * <p>
     * 后来当我要改的时候又发现问题了
     * 我使用了StepDetector.CURRENT_STEP = (int)event.values[0];
     * 所以它会返回这一个月的步数，当每次传感器发生改变时，我直接让CURRENT_STEP++；就可以从0开始自增了
     * 不过上面的分析依然正确。不过当使用CURRENT_STEP++如果服务停掉计步就不准了。如果使用计步传感器中
     * 统计的数据减去之前的数据就是当天的数据了，这样每天走多少步就能准确的显示出来
     */
    private void addCountStepListener() {
        Sensor detectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        Sensor countSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (countSensor != null) {
            stepSensor = 0;
            Log.v(TAG, "countSensor 计步传感器");
            sensorManager.registerListener(this, countSensor, SensorManager.SENSOR_DELAY_UI);
        } else if (detectorSensor != null) {
            stepSensor = 1;
            Log.v(TAG, "detector 步行检测传感器");
            sensorManager.registerListener(StepService.this, detectorSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            stepSensor = 2;
            Log.v(TAG, "Count sensor not available! 没有可用的传感器，只能用加速传感器");
            addBasePedoListener();
        }
    }


    /**
     * 使用加速度传感器
     */
    private void addBasePedoListener() {
        stepDetector = new StepDetector(this);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(stepDetector, sensor, SensorManager.SENSOR_DELAY_UI);
        stepDetector.setOnSensorChangeListener(new StepDetector.OnSensorChangeListener() {
            @Override
            public void onChange() {
                updateNotification("今日步数：" + StepDetector.CURRENT_STEP + " 步");
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Timber.d("当前步数" + (long) event.values[0]);
        stepTotal=(long)event.values[0];
        if (stepSensor == 0) {
            if (isNewDay) {
                previousStep = (long) event.values[0];
                isNewDay = false;
                save();
            } else {
                StepInfo stepInfo = DBHelper.getStepInfo(CURRENTDATE);
                this.previousStep = stepInfo.getPreviousStepCount();
            }
            StepDetector.CURRENT_STEP = (long) event.values[0] - previousStep;
        } else if (stepSensor == 1) {
            StepDetector.CURRENT_STEP++;
        }
        //更新状态栏信息
        updateNotification("今日步数：" + StepDetector.CURRENT_STEP + " 步");

    }

    private void save() {
        long tempStep = StepDetector.CURRENT_STEP;
        StepInfo stepInfo = DBHelper.getStepInfo(CURRENTDATE);
        if (stepInfo == null) {
            stepInfo = new StepInfo();
            stepInfo.setCreteTime(DateUtil.getTodayTime(DateUtil.DATE_FULL_STR));
            stepInfo.setDate(CURRENTDATE);
            stepInfo.setStepCount(tempStep);
            stepInfo.setStepTotal(stepTotal);
            stepInfo.setPreviousStepCount(previousStep);
            DBHelper.insertStepInfo(stepInfo);
        } else {
            stepInfo.setStepCount(tempStep);
            stepInfo.setStepTotal(stepTotal);
            stepInfo.setPreviousStepCount(previousStep);
            DBHelper.updateStepInfo(stepInfo);
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    synchronized private PowerManager.WakeLock getLock(Context context) {
        if (mWakeLock != null) {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            mWakeLock = null;
        }
        if (mWakeLock == null) {
            PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, StepService.class.getName());
            mWakeLock.setReferenceCounted(true);
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis((System.currentTimeMillis()));
            int hour = c.get(Calendar.HOUR_OF_DAY);
            if (hour >= 23 || hour <= 6) {
                mWakeLock.acquire(5000);
            } else {
                mWakeLock.acquire(300000);
            }
        }
        return (mWakeLock);
    }

    class TimeCount extends CountDownTimer {
        public TimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish() {
            time.cancel();
            save();
            startTimeCount();
        }
    }

    private void updateNotification(String content) {
        boolean isShowNotification = Preferences.getIsShowNotification(StepService.this);
        if (isShowNotification) {
            builder = new android.support.v7.app.NotificationCompat.Builder(this);
            builder.setPriority(Notification.PRIORITY_MIN);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class), 0);
            builder.setContentIntent(contentIntent);
            builder.setSmallIcon(R.mipmap.ic_launcher);
            builder.setTicker("Pedometer");
            builder.setContentTitle("Pedometer");
            builder.setOngoing(true);
            builder.setContentText(content);
            Notification notification = builder.build();
            startForeground(0, notification);
            nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(R.string.app_name, notification);
        } else {
            Log.i(TAG, "Do you have setting preferences is show Notification !");
        }
    }
}
