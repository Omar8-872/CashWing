package com.example.quickpay;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class CharityActivity extends AppCompatActivity {
    private String phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charity);
        Anim.fadeIn(this, findViewById(R.id.textTitle));
        Anim.handleBack(this);

        phone = getIntent().getStringExtra(HomeActivity.KEY_PHONE);
        if (phone == null) {
            phone = "";
        }

        LinearLayout container = findViewById(R.id.listContainer);
        addSection(container, R.string.cat_hospitals, Charities.inCategory(Charities.HOSPITALS));
        addSection(container, R.string.cat_orphans, Charities.inCategory(Charities.ORPHANS));
        addSection(container, R.string.cat_more, Charities.inCategory(Charities.MORE));

        TextView note = new TextView(this);
        note.setText(R.string.ch_note);
        note.setTextColor(getColor(R.color.pay_gray));
        note.setTextSize(12);
        note.setPadding(0, 8, 0, 0);
        container.addView(note);

        Button btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(v -> {
            Anim.pulse(this, v);
            Intent intent = new Intent(CharityActivity.this, HomeActivity.class);
            intent.putExtra(HomeActivity.KEY_PHONE, phone);
            Anim.openHome(this, intent);
        });
    }

    private void addSection(LinearLayout container, int titleRes, List<Charities.Item> items) {
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(getColor(R.color.pay_dark));
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(0, 16, 0, 10);
        container.addView(title);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Charities.Item item : items) {
            View row = inflater.inflate(R.layout.item_charity, container, false);
            ((ImageView) row.findViewById(R.id.imgIcon)).setImageResource(item.iconRes);
            ((TextView) row.findViewById(R.id.textName)).setText(item.nameRes);
            ((TextView) row.findViewById(R.id.textDesc)).setText(item.descRes);
            row.setOnClickListener(v -> {
                Intent intent = new Intent(CharityActivity.this, TransferActivity.class);
                intent.putExtra(HomeActivity.KEY_PHONE, phone);
                intent.putExtra(TransferActivity.KEY_CHARITY, true);
                intent.putExtra(TransferActivity.KEY_CHARITY_ID, item.id);
                Anim.open(this, intent);
            });
            container.addView(row);
        }
    }
}
