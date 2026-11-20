package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {
    private EditText editName, editPhone, editPin;
    private Button btnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        Anim.handleBack(this);
        Anim.fadeIn(this, findViewById(R.id.textTitle));

        editName = findViewById(R.id.editName);
        editPhone = findViewById(R.id.editPhone);
        editPin = findViewById(R.id.editPin);
        btnCreate = findViewById(R.id.btnCreate);

        btnCreate.setOnClickListener(v -> {
            Anim.pulse(this, v);
            createAccount();
        });
    }

    private void createAccount() {
        String name = editName.getText().toString().trim();
        String phone = DatabaseHelper.normalize(editPhone.getText().toString().trim());
        String pin = DatabaseHelper.normalize(editPin.getText().toString().trim());

        if (name.isEmpty() || phone.isEmpty() || pin.isEmpty()) {
            Toast.makeText(this, R.string.err_fill_all, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!phone.matches("[0-9]{11}")) {
            Toast.makeText(this, R.string.err_phone_11, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!phone.matches("01[0-9]{9}")) {
            Toast.makeText(this, R.string.err_phone_mobile, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pin.matches("[0-9]{8,12}")) {
            Toast.makeText(this, R.string.err_pin_range, Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreate.setEnabled(false); // avoid pressing twice while we wait for the server
        Cloud.createUser(this, name, phone, pin, result -> {
            btnCreate.setEnabled(true);
            if (result.equals(Cloud.OK)) {
                Toast.makeText(RegisterActivity.this, R.string.account_created, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                Anim.openClear(RegisterActivity.this, intent);
            } else {
                Toast.makeText(RegisterActivity.this, result, Toast.LENGTH_LONG).show();
                Anim.shake(RegisterActivity.this, editPhone);
            }
        });
    }
}
