package com.example.quickpay;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    public static final String KEY_PHONE = "PHONE";
    private String phone;
    private TextView textWelcome, textBalance, textRecent;
    private DatabaseHelper db;
    private View[] money;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Anim.zoomIn(this, findViewById(R.id.imgWallet));
        Anim.fadeIn(this, findViewById(R.id.textWelcome));
        Anim.zoomIn(this, findViewById(R.id.textBalance));

        textWelcome = findViewById(R.id.textWelcome);
        textBalance = findViewById(R.id.textBalance);
        textRecent = findViewById(R.id.textRecent);
        money = new View[]{findViewById(R.id.money1), findViewById(R.id.money2), findViewById(R.id.money3),
                findViewById(R.id.money4), findViewById(R.id.money5)};
        Button btnSend = findViewById(R.id.btnSend);
        Button btnDonate = findViewById(R.id.btnDonate);
        Button btnHistory = findViewById(R.id.btnHistory);
        Button btnProfile = findViewById(R.id.btnProfile);
        Button btnLogout = findViewById(R.id.btnLogout);

        db = new DatabaseHelper(this);
        phone = getIntent().getStringExtra(KEY_PHONE);

        if (phone == null || phone.trim().isEmpty()) {
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            Anim.openClear(this, intent);
            return;
        }

        Cloud.startSync(this, phone); // keeps the local copy up to date from the server

        btnSend.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(HomeActivity.this, TransferActivity.class);
            intent.putExtra(KEY_PHONE, phone);
            Anim.open(this, intent);
        });

        btnDonate.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(HomeActivity.this, CharityActivity.class);
            intent.putExtra(KEY_PHONE, phone);
            Anim.open(this, intent);
        });

        btnHistory.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(HomeActivity.this, HistoryActivity.class);
            intent.putExtra(KEY_PHONE, phone);
            Anim.open(this, intent);
        });

        btnProfile.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            intent.putExtra(KEY_PHONE, phone);
            Anim.open(this, intent);
        });

        btnLogout.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Cloud.logout(this, phone);
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            Anim.openClear(this, intent);
        });
    }

    @Override
    protected void onResume() { // refresh balance when coming back
        super.onResume();
        if (phone != null && !phone.trim().isEmpty()) {
            refresh();
            Cloud.setOnChange(this::refresh); // and again whenever new data arrives from the cloud
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Cloud.setOnChange(null);
    }

    private void refresh() {
        textWelcome.setText(getString(R.string.welcome_name, db.getName(phone)));
        textBalance.setText(DatabaseHelper.money(db.getBalance(phone)));
        textRecent.setText(db.getHistory(phone, 3, true));
        checkNewMoney();
    }

    // shows the flying money once when this user has received new money
    private void checkNewMoney() {
        SharedPreferences prefs = getSharedPreferences("cashwing", MODE_PRIVATE);
        long lastId = db.getLastReceivedId(phone);
        long seen = prefs.getLong("seen_" + phone, 0);
        if (seen > lastId) {
            seen = 0; // the database was reset
        }

        if (lastId > seen) {
            String message = db.getReceivedSince(phone, seen);
            prefs.edit().putLong("seen_" + phone, lastId).apply();
            Anim.flyMoney(this, money, false); // the receiver sees the money fall in
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}
