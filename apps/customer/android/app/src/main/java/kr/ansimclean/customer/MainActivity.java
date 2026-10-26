package kr.ansimclean.customer;

import android.os.Bundle;
import android.os.Build;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.view.View;
import android.view.ViewGroup;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        silenceViews(getWindow().getDecorView());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 기존 채널의 소리 설정은 Android에서 변경할 수 없으므로 새 무음 채널로 전환한다.
            NotificationChannel channel = new NotificationChannel(
                "ansimclean_silent_v1", getString(R.string.app_name) + " 알림", NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        silenceViews(getWindow().getDecorView());
    }

    private void silenceViews(View view) {
        view.setSoundEffectsEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) silenceViews(group.getChildAt(i));
        }
    }
}
