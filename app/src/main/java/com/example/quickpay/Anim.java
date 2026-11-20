package com.example.quickpay;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class Anim {

    public static void fadeIn(Activity a, View v) {
        Animation anim = AnimationUtils.loadAnimation(a, R.anim.fade_in);
        v.startAnimation(anim);
    }

    public static void zoomIn(Activity a, View v) {
        Animation anim = AnimationUtils.loadAnimation(a, R.anim.zoom_in);
        v.startAnimation(anim);
    }

    public static void shake(Activity a, View v) {
        Animation anim = AnimationUtils.loadAnimation(a, R.anim.shake);
        v.startAnimation(anim);
    }

    public static void pulse(Activity a, View v) {
        Animation anim = AnimationUtils.loadAnimation(a, R.anim.pulse);
        v.startAnimation(anim);
    }

    // up = true: the money flies away (sender), false: the money falls in (receiver)
    public static void flyMoney(Activity a, View[] money, boolean up) {
        for (int i = 0; i < money.length; i++) {
            Animation anim = AnimationUtils.loadAnimation(a, up ? R.anim.fly_up : R.anim.fly_down);
            anim.setStartOffset(i * 250);
            money[i].setVisibility(View.VISIBLE);
            money[i].startAnimation(anim);
        }
    }

    private static long lastOpen = 0;

    // Two quick taps must not open the same screen twice
    public static void open(Activity a, Intent intent) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastOpen < 700) {
            return;
        }
        lastOpen = now;
        a.startActivity(intent);
        a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    public static void close(Activity a) {
        a.finish();
        a.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // The phone's Back button / gesture closes the screen with the same slide animation as our buttons
    public static void handleBack(AppCompatActivity a) {
        a.getOnBackPressedDispatcher().addCallback(a, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                close(a);
            }
        });
    }

    // Back from the result screen goes Home (never back to the confirm screen: it would repeat the transfer)
    public static void handleBackHome(AppCompatActivity a, String phone) {
        a.getOnBackPressedDispatcher().addCallback(a, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(a, HomeActivity.class);
                intent.putExtra(HomeActivity.KEY_PHONE, phone);
                openHome(a, intent);
            }
        });
    }

    public static void openClear(Activity a, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(intent);
        a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    public static void openHome(Activity a, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        a.startActivity(intent);
        a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
