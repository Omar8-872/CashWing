package com.example.quickpay;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {
    private TextView textMonth, textSummary, textHistory;
    private BarChartView chart;
    private DatabaseHelper db;
    private String phone;
    private Calendar month; // the month on screen (always day 1)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        Anim.handleBack(this);
        Anim.fadeIn(this, findViewById(R.id.textTitle));

        textMonth = findViewById(R.id.textMonth);
        textSummary = findViewById(R.id.textSummary);
        textHistory = findViewById(R.id.textHistory);
        chart = findViewById(R.id.chartMonths);
        Button btnPrev = findViewById(R.id.btnPrev);
        Button btnNext = findViewById(R.id.btnNext);
        Button btnBack = findViewById(R.id.btnBack);

        phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        if (phone == null) {
            phone = "";
        }

        db = new DatabaseHelper(this);
        month = Calendar.getInstance();
        month.set(Calendar.DAY_OF_MONTH, 1);
        showMonth();

        btnPrev.setOnClickListener(v -> {
            Anim.pulse(this, v);
            month.add(Calendar.MONTH, -1);
            showMonth();
        });

        btnNext.setOnClickListener(v -> {
            Anim.pulse(this, v);
            if (isCurrentMonth()) {
                return; // no future months
            }
            month.add(Calendar.MONTH, 1);
            showMonth();
        });

        btnBack.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Anim.close(this);
        });
    }

    private boolean isCurrentMonth() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) == month.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == month.get(Calendar.MONTH);
    }

    private void showMonth() {
        String key = DatabaseHelper.monthKey(month);

        textMonth.setText(new SimpleDateFormat("MMMM yyyy", Lang.labelLocale()).format(month.getTime()));
        textSummary.setText(getString(R.string.sent_label) + " " + DatabaseHelper.money(db.getSentInMonth(phone, key))
                + "\n" + getString(R.string.received_label) + " " + DatabaseHelper.money(db.getReceivedInMonth(phone, key))
                + "\n" + getString(R.string.transactions_label) + " " + db.getCountInMonth(phone, key));
        chart.setData(db.getMonthlySent(phone, month, 6), DatabaseHelper.monthLabels(month, 6));
        textHistory.setText(db.getHistory(phone, 100, false, "All", "", key));
    }
}
