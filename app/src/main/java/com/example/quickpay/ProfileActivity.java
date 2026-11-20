package com.example.quickpay;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {
    private static final String SUPPORT_PHONE = "01011475826"; // admin / customer service
    private DatabaseHelper db;
    private String phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Anim.handleBack(this);
        Anim.zoomIn(this, findViewById(R.id.imgProfile));
        Anim.fadeIn(this, findViewById(R.id.textName));

        TextView textName = findViewById(R.id.textName);
        TextView textPhone = findViewById(R.id.textPhone);
        TextView textBalance = findViewById(R.id.textBalance);
        TextView textSent = findViewById(R.id.textSent);
        TextView textReceived = findViewById(R.id.textReceived);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnReport = findViewById(R.id.btnReport);
        Button btnChangePin = findViewById(R.id.btnChangePin);
        Button btnSupport = findViewById(R.id.btnSupport);
        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnDelete = findViewById(R.id.btnDelete);
        Button btnLanguage = findViewById(R.id.btnLanguage);
        btnLanguage.setOnClickListener(v -> Lang.toggle());

        phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        if (phone == null) {
            phone = "";
        }

        db = new DatabaseHelper(this);
        textName.setText(db.getName(phone));
        textPhone.setText(phone);
        textBalance.setText(getString(R.string.balance_label) + " " + DatabaseHelper.money(db.getBalance(phone)));
        textSent.setText(getString(R.string.total_sent_label) + " " + DatabaseHelper.money(db.getTotalSent(phone)));
        textReceived.setText(getString(R.string.total_received_label) + " " + DatabaseHelper.money(db.getTotalReceived(phone)));

        BarChartView chart = findViewById(R.id.chartSpending);
        chart.setData(db.getDailySent(phone, 7), DatabaseHelper.lastDays(7));

        btnBack.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Anim.close(this);
        });

        btnReport.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(ProfileActivity.this, ReportActivity.class);
            intent.putExtra(HomeActivity.KEY_PHONE, phone);
            Anim.open(this, intent);
        });

        btnChangePin.setOnClickListener(v -> {
            Anim.pulse(this, v);
            showChangePinDialog();
        });

        btnSupport.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + SUPPORT_PHONE));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.err_no_phone_app, Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Cloud.logout(this, phone);
            Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
            Anim.openClear(this, intent);
        });

        btnDelete.setOnClickListener(v -> {
            Anim.pulse(this, v);
            showDeleteDialog();
        });
    }

    private void showChangePinDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_change_pin, null);
        EditText editOldPin = view.findViewById(R.id.editOldPin);
        EditText editNewPin = view.findViewById(R.id.editNewPin);

        new AlertDialog.Builder(this)
                .setTitle(R.string.change_pin)
                .setView(view)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String oldPin = DatabaseHelper.normalize(editOldPin.getText().toString().trim());
                    String newPin = DatabaseHelper.normalize(editNewPin.getText().toString().trim());

                    if (!newPin.matches("[0-9]{8,12}")) {
                        Toast.makeText(this, R.string.err_pin_range, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Cloud.checkPin(this, phone, oldPin, check -> {
                        if (check.equals(Cloud.WRONG)) {
                            Toast.makeText(this, R.string.err_wrong_current_pin, Toast.LENGTH_SHORT).show();
                        } else if (!check.equals(Cloud.OK)) {
                            Toast.makeText(this, check, Toast.LENGTH_LONG).show();
                        } else {
                            Cloud.changePin(this, phone, oldPin, newPin, result -> {
                                if (result.equals(Cloud.OK)) {
                                    Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_delete_account, null);
        TextView textInfo = view.findViewById(R.id.textDeleteInfo);
        EditText editPin = view.findViewById(R.id.editDeletePin);
        textInfo.setText(getString(R.string.delete_info, DatabaseHelper.money(db.getBalance(phone))));

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account)
                .setView(view)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    String pin = DatabaseHelper.normalize(editPin.getText().toString().trim());

                    Cloud.checkPin(this, phone, pin, check -> {
                        if (check.equals(Cloud.WRONG)) {
                            Toast.makeText(this, R.string.err_wrong_pin, Toast.LENGTH_SHORT).show();
                        } else if (!check.equals(Cloud.OK)) {
                            Toast.makeText(this, check, Toast.LENGTH_LONG).show();
                        } else {
                            Cloud.deleteUser(this, phone, pin, result -> {
                                if (!result.equals(Cloud.OK)) {
                                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                                    return;
                                }
                                Cloud.stopSync();
                                Toast.makeText(this, R.string.account_deleted, Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
                                Anim.openClear(this, intent);
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
