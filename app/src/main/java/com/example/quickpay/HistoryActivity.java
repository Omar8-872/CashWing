package com.example.quickpay;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HistoryActivity extends AppCompatActivity {
    private TextView textHistory;
    private EditText editSearch;
    private RadioGroup radioFilter;
    private DatabaseHelper db;
    private String phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        Anim.handleBack(this);
        Anim.fadeIn(this, findViewById(R.id.textHistory));

        textHistory = findViewById(R.id.textHistory);
        editSearch = findViewById(R.id.editSearch);
        radioFilter = findViewById(R.id.radioFilter);
        Button btnHome = findViewById(R.id.btnHome);

        phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        if (phone == null) {
            phone = "";
        }

        db = new DatabaseHelper(this);
        showHistory();

        // update the list while the user types or changes the filter
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showHistory();
            }
        });

        radioFilter.setOnCheckedChangeListener((group, checkedId) -> showHistory());

        btnHome.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Anim.close(this);
        });
    }

    private void showHistory() {
        String filter = "All";
        int id = radioFilter.getCheckedRadioButtonId();
        if (id == R.id.radioSent) {
            filter = "Sent";
        } else if (id == R.id.radioReceived) {
            filter = "Received";
        } else if (id == R.id.radioRejected) {
            filter = "Rejected";
        }

        String search = DatabaseHelper.normalize(editSearch.getText().toString());
        textHistory.setText(db.getHistory(phone, 100, false, filter, search));
    }
}
