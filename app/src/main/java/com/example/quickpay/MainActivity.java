package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private EditText editPhone, editPin;
    private Button btnLogin, btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Anim.zoomIn(this, findViewById(R.id.imgLogo));
        Anim.fadeIn(this, findViewById(R.id.textTitle));

        new DatabaseHelper(this); // creates the local copy and sets the currency text

        editPhone = findViewById(R.id.editPhone);
        editPin = findViewById(R.id.editPin);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        Button btnLanguage = findViewById(R.id.btnLanguage);
        btnLanguage.setOnClickListener(v -> Lang.toggle());

        btnLogin.setOnClickListener(v -> {
            Anim.pulse(this, v);
            loginUser();
        });

        btnRegister.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            Anim.open(this, intent);
        });
    }

    private void loginUser() {
        String phone = DatabaseHelper.normalize(editPhone.getText().toString().trim());
        String pin = DatabaseHelper.normalize(editPin.getText().toString().trim());

        if (phone.isEmpty() || pin.isEmpty()) {
            Toast.makeText(this, R.string.err_enter_phone_pin, Toast.LENGTH_SHORT).show();
            Anim.shake(this, editPhone);
            return;
        }

        btnLogin.setEnabled(false);
        Cloud.login(this, phone, pin, result -> { // "OK" or an error message
            btnLogin.setEnabled(true);
            if (result.equals(Cloud.OK)) {
                Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                intent.putExtra(HomeActivity.KEY_PHONE, phone);
                Anim.open(MainActivity.this, intent);
                finish();
            } else {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                Anim.shake(MainActivity.this, editPin);
            }
        });
    }
}
