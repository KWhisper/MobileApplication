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
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;
import android.support.v4.app.Fragment;
import com.calypso.pedometer.Adapter.ViewPagerAdapter;
import com.calypso.pedometer.constant.Preferences;
import com.calypso.pedometer.R;
import com.calypso.pedometer.constant.Constant;
import com.calypso.pedometer.greendao.DBHelper;
import com.calypso.pedometer.greendao.entry.StepInfo;
import com.calypso.pedometer.stepdetector.StepService;
import com.calypso.pedometer.utils.ConversionUtil;
import com.calypso.pedometer.utils.DateUtil;
import com.calypso.pedometer.utils.Timber;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements Handler.Callback {

    private Messenger messenger;
    private Messenger mGetReplyMessenger = new Messenger(new Handler(this));
    private Handler delayHandler;
    private DecimalFormat df1 = new DecimalFormat("0.00");
    private int stepBenchmark = 0;

    //=========================
    private BottomNavigationView bottomNavigationView;
    private ViewPagerAdapter viewPagerAdapter;
    private ViewPager viewPager;
    private MenuItem menuItem;
    //=========================

    //以bind形式开启service，故有ServiceConnection接收回调
    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                messenger = new Messenger(service);
                Message msg = Message.obtain(null, Constant.MSG_FROM_CLIENT);
                msg.replyTo = mGetReplyMessenger;
                messenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.tag("MainActivity");
        setContentView(R.layout.activity_main);
        initData();
        initFragment();
    }

    public void initFragment(){
        bottomNavigationView = (BottomNavigationView) findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        BottomNavigationViewHelper.disableShiftMode(bottomNavigationView);
        viewPager = (ViewPager) findViewById(R.id.vp);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (menuItem != null) {
                    menuItem.setChecked(false);
                } else {
                    bottomNavigationView.getMenu().getItem(0).setChecked(false);
                }
                menuItem = bottomNavigationView.getMenu().getItem(position);
                menuItem.setChecked(true);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(viewPagerAdapter);
        List<Fragment> list = new ArrayList<>();
        StepPedometer stepTest = new StepPedometer();
        list.add(stepTest);
        //list.add(stepTest);
        //list.add();
        //list.add(TestFragment.newInstance("个人"));
        viewPagerAdapter.setList(list);
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            menuItem = item;
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    viewPager.setCurrentItem(0);
                    return true;
                case R.id.navigation_dashboard:
                    viewPager.setCurrentItem(1);
                    return true;
                case R.id.navigation_notifications:
                    viewPager.setCurrentItem(2);
                    return true;
                case R.id.navigation_person:
                    viewPager.setCurrentItem(3);
                    return true;
            }
            return false;
        }
    };


    public void initData() {
        stepBenchmark = Preferences.getStepBenchmark(MainActivity.this);
        delayHandler = new Handler(this);
        Intent intent = new Intent(this, StepService.class);
        bindService(intent, conn, BIND_AUTO_CREATE);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case Constant.MSG_FROM_SERVER:

                delayHandler.sendEmptyMessageDelayed(Constant.REQUEST_SERVER, Constant.TIME_INTERVAL);
                //colorArcProgressBar.setCurrentValues((int) step);
                break;
            case Constant.REQUEST_SERVER:
                try {
                    Message msgl = Message.obtain(null, Constant.MSG_FROM_CLIENT);
                    msgl.replyTo = mGetReplyMessenger;
                    messenger.send(msgl);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;
        }
        return false;
    }

}
