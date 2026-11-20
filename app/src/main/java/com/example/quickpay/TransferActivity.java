package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TransferActivity extends AppCompatActivity {
    public static final String KEY_CHARITY = "CHARITY";
    public static final String KEY_CHARITY_ID = "CHARITY_ID";
    private final double MAX_AMOUNT = 1000000;

    private EditText editReceiver, editAmount;
    private TextView textBalance;
    private Button btnSend;
    private DatabaseHelper db;
    private String phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        Anim.handleBack(this);

        db = new DatabaseHelper(this);

        editReceiver = findViewById(R.id.editReceiver);
        editAmount = findViewById(R.id.editAmount);
        textBalance = findViewById(R.id.textBalance);
        btnSend = findViewById(R.id.btnSend);
        TextView textTitle = findViewById(R.id.textTitle);
        ImageView imgSend = findViewById(R.id.imgSend);

        phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        if (phone == null) {
            phone = "";
        }

        boolean charity = getIntent().getBooleanExtra(KEY_CHARITY, false);
        if (charity) {
            String charityId = getIntent().getStringExtra(KEY_CHARITY_ID);
            if (charityId == null || !Charities.isCharity(charityId)) {
                charityId = DatabaseHelper.CHARITY_PHONE;
            }
            textTitle.setText(db.getName(charityId));
            imgSend.setImageResource(Charities.iconOf(charityId));
            editReceiver.setText(charityId);
            editReceiver.setVisibility(View.GONE); // the charity is already chosen
            editAmount.setHint(R.string.hint_donation_amount);

            // quick amounts
            findViewById(R.id.layoutChips).setVisibility(View.VISIBLE);
            int[] chipIds = {R.id.chip1, R.id.chip2, R.id.chip3, R.id.chip4};
            String[] amounts = {"50", "100", "500", "1000"};
            for (int i = 0; i < chipIds.length; i++) {
                final String amount = amounts[i];
                findViewById(chipIds[i]).setOnClickListener(v -> editAmount.setText(amount));
            }
        }

        btnSend.setOnClickListener(v -> {
            Anim.pulse(this, v);
            sendMoney();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        textBalance.setText(getString(R.string.balance_label) + " " + DatabaseHelper.money(db.getBalance(phone)));
    }

    private void sendMoney() {
        String receiver = DatabaseHelper.normalize(editReceiver.getText().toString().trim());
        String s = DatabaseHelper.normalize(editAmount.getText().toString().trim());

        if (receiver.isEmpty() || s.isEmpty()) {
            Toast.makeText(this, R.string.err_enter_receiver_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        if (receiver.equals(phone)) {
            Toast.makeText(this, R.string.err_self_transfer, Toast.LENGTH_SHORT).show();
            Anim.shake(this, editReceiver);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.err_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        amount = Math.round(amount * 100) / 100.0;

        if (amount <= 0) {
            Toast.makeText(this, R.string.err_amount_zero, Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount > MAX_AMOUNT) {
            Toast.makeText(this, R.string.err_max_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        final double finalAmount = amount;
        btnSend.setEnabled(false);
        Cloud.checkReceiver(this, receiver, result -> { // does the receiver exist on the server?
            btnSend.setEnabled(true);
            if (!result.equals(Cloud.OK)) {
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
                Anim.shake(this, editReceiver);
                return;
            }

            if (db.getBalance(phone) + 0.001 < finalAmount) {
                Cloud.recordRejected(this, phone, receiver, finalAmount); // saved as Rejected in the history
                Toast.makeText(this, getString(R.string.err_insufficient_balance_is,
                        DatabaseHelper.money(db.getBalance(phone))), Toast.LENGTH_LONG).show();
                Anim.shake(this, editAmount);
                return;
            }

            Intent intent = new Intent(TransferActivity.this, ConfirmActivity.class);
            intent.putExtra(HomeActivity.KEY_PHONE, phone);
            intent.putExtra(ResultActivity.KEY_RECEIVER, receiver);
            intent.putExtra(ResultActivity.KEY_AMOUNT, String.valueOf(finalAmount));
            Anim.open(this, intent);
        });
    }
}
