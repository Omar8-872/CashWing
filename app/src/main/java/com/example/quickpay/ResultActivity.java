package com.example.quickpay;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {
    public static final String KEY_RECEIVER = "RECEIVER";
    public static final String KEY_AMOUNT = "AMOUNT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        Anim.zoomIn(this, findViewById(R.id.imgCheck));
        Anim.zoomIn(this, findViewById(R.id.textAmount));

        TextView textAmount = findViewById(R.id.textAmount);
        TextView textReceiver = findViewById(R.id.textReceiver);
        TextView textNewBalance = findViewById(R.id.textNewBalance);
        View[] money = {findViewById(R.id.money1), findViewById(R.id.money2), findViewById(R.id.money3),
                findViewById(R.id.money4), findViewById(R.id.money5)};
        Button btnCall = findViewById(R.id.btnCall);
        Button btnHome = findViewById(R.id.btnHome);

        String phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        String receiver = getIntent().getStringExtra(KEY_RECEIVER);
        String amount = getIntent().getStringExtra(KEY_AMOUNT);

        if (phone == null) {
            phone = "";
        }
        Anim.handleBackHome(this, phone);
        if (receiver != null && Charities.isCharity(receiver)) {
            btnCall.setVisibility(View.GONE); // a charity has no phone to call
        }
        if (receiver == null || receiver.trim().isEmpty()) {
            receiver = getString(R.string.unknown);
        }
        if (amount == null || amount.trim().isEmpty()) {
            amount = getString(R.string.zero_egp_short);
        }

        DatabaseHelper db = new DatabaseHelper(this);
        textAmount.setText(amount);
        if (Charities.isCharity(receiver)) {
            textReceiver.setText(getString(R.string.sent_to) + " " + db.getName(receiver));
        } else {
            textReceiver.setText(getString(R.string.sent_to) + " " + db.getName(receiver) + " (" + receiver + ")");
        }
        textNewBalance.setText(getString(R.string.new_balance_label) + " " + DatabaseHelper.money(db.getBalance(phone)));

        Anim.flyMoney(this, money, true); // the sender sees the money fly away

        final String receiverPhone = receiver;
        btnCall.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Uri number = Uri.parse("tel:" + receiverPhone); // opens the dialer with the receiver's number
            Intent intent = new Intent(Intent.ACTION_DIAL, number);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.err_no_phone_app, Toast.LENGTH_SHORT).show();
            }
        });

        final String userPhone = phone;
        btnHome.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
            intent.putExtra(HomeActivity.KEY_PHONE, userPhone);
            Anim.openHome(this, intent);
        });
    }
}
