package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ConfirmActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        Anim.handleBack(this);
        Anim.zoomIn(this, findViewById(R.id.textAmount));

        TextView textTo = findViewById(R.id.textTo);
        TextView textAmount = findViewById(R.id.textAmount);
        TextView textAfter = findViewById(R.id.textAfter);
        Button btnConfirm = findViewById(R.id.btnConfirm);
        Button btnCancel = findViewById(R.id.btnCancel);

        String phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        String receiver = getIntent().getStringExtra(ResultActivity.KEY_RECEIVER);
        String amountText = getIntent().getStringExtra(ResultActivity.KEY_AMOUNT);

        if (phone == null || receiver == null || amountText == null) {
            Toast.makeText(this, R.string.err_missing_data, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        double amount = 0;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.err_invalid_amount, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        DatabaseHelper db = new DatabaseHelper(this);
        if (Charities.isCharity(receiver)) {
            textTo.setText(db.getName(receiver)); // a charity has no phone number to show
        } else {
            textTo.setText(db.getName(receiver) + "\n" + receiver);
        }
        textAmount.setText(DatabaseHelper.money(amount));
        textAfter.setText(getString(R.string.balance_after_label) + " " + DatabaseHelper.money(db.getBalance(phone) - amount));

        final double finalAmount = amount;
        btnConfirm.setOnClickListener(v -> {
            Anim.pulse(this, v);
            btnConfirm.setEnabled(false); // avoid sending twice
            Cloud.transfer(this, phone, receiver, finalAmount, result -> {
                if (result.equals(Cloud.OK)) {
                    Intent intent = new Intent(ConfirmActivity.this, ResultActivity.class);
                    intent.putExtra(HomeActivity.KEY_PHONE, phone);
                    intent.putExtra(ResultActivity.KEY_RECEIVER, receiver);
                    intent.putExtra(ResultActivity.KEY_AMOUNT, DatabaseHelper.money(finalAmount));
                    Anim.open(this, intent);
                    finish();
                } else {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                    Anim.close(this);
                }
            });
        });

        btnCancel.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Anim.close(this);
        });
    }
}
