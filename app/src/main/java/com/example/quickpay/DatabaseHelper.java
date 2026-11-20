package com.example.quickpay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "quickpay.db";
    private static final int DB_VERSION = 5; // v5: this file is only a fast local copy of the server data

    public static final String CHARITY_PHONE = "00000000000";
    public static final String CHARITY_NAME = "Charity Fund";

    public static final String COMPLETED = "Completed";
    public static final String REJECTED = "Rejected";

    private final Context ctx; // used to read the texts of the current language
    private static String currency = "EGP";

    public DatabaseHelper(Context context) {
        this(context, false);
    }

    // For the sync code in the background: it only writes, so it must not change the shown currency text
    public static DatabaseHelper writer(Context context) {
        return new DatabaseHelper(context.getApplicationContext(), true);
    }

    private DatabaseHelper(Context context, boolean writerOnly) {
        super(context, DB_NAME, null, DB_VERSION);
        ctx = context;
        if (!writerOnly) {
            currency = context.getString(R.string.currency);
        }
    }

    // This database is only a local COPY of what is on the server (the cloud is the real data).
    // CloudSync fills it, and the screens read from it because it is fast.
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users ("
                + "phone TEXT PRIMARY KEY, "
                + "name TEXT, "
                + "balance REAL DEFAULT 0, "
                + "active INTEGER DEFAULT 1)");          // 0 = deleted account

        // id = the time of the transfer in milliseconds, so it is the same on every device
        db.execSQL("CREATE TABLE transactions ("
                + "id INTEGER PRIMARY KEY, "
                + "sender TEXT, "
                + "receiver TEXT, "
                + "amount REAL, "
                + "status TEXT, "
                + "time TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS users");
        db.execSQL("DROP TABLE IF EXISTS transactions");
        onCreate(db);
    }

    // ---- writing the local copy (called only by CloudSync) ----

    public void saveUser(String phone, String name, double balance, boolean active) {
        ContentValues v = new ContentValues();
        v.put("phone", phone);
        v.put("name", name);
        v.put("balance", balance);
        v.put("active", active ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("users", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // name and state of ANOTHER user (we do not know his balance, so it is not touched)
    public void saveOtherUser(String phone, String name, boolean active) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO users (phone, name, balance, active) VALUES (?, ?, 0, ?)",
                new Object[]{phone, name, active ? 1 : 0});
        db.execSQL("UPDATE users SET name = ?, active = ? WHERE phone = ?",
                new Object[]{name, active ? 1 : 0, phone});
    }

    public void setBalance(String phone, double balance) {
        getWritableDatabase().execSQL("UPDATE users SET balance = ? WHERE phone = ?",
                new Object[]{balance, phone});
    }

    public void saveTransaction(long id, String sender, String receiver, double amount, String status) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date(id));
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("sender", sender);
        v.put("receiver", receiver);
        v.put("amount", amount);
        v.put("status", status);
        v.put("time", time);
        getWritableDatabase().insertWithOnConflict("transactions", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deactivate(String phone) {
        getWritableDatabase().execSQL("UPDATE users SET active = 0, balance = 0 WHERE phone = ?",
                new Object[]{phone});
    }

    // do we already have this user in the local copy?
    public boolean knowsUser(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT phone FROM users WHERE phone = ?", new String[]{phone});
        boolean known = c.moveToFirst();
        c.close();
        return known;
    }

    // ---- reading (used by the screens) ----

    public String getName(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT name, active FROM users WHERE phone = ?",
                new String[]{phone});
        String name = ctx.getString(R.string.unknown);
        if (c.moveToFirst()) {
            if (c.getInt(1) == 1) {
                name = c.getString(0);
            } else {
                name = ctx.getString(R.string.deleted_account);
            }
        }
        c.close();
        Charities.Item charity = Charities.find(phone);
        if (charity != null) {
            name = ctx.getString(charity.nameRes);
        }
        return name;
    }

    public double getBalance(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT balance FROM users WHERE phone = ?",
                new String[]{phone});
        double balance = 0;
        if (c.moveToFirst()) {
            balance = c.getDouble(0);
        }
        c.close();
        return balance;
    }

    // the status is saved in English, but shown in the current language
    private String statusLabel(String status) {
        if (REJECTED.equals(status)) {
            return ctx.getString(R.string.status_rejected);
        }
        return ctx.getString(R.string.status_completed);
    }

    public String getHistory(String phone, int limit, boolean compact) {
        return getHistory(phone, limit, compact, "All", "");
    }

    // filter: All / Sent / Received / Rejected, search: name or phone of the other person
    public String getHistory(String phone, int limit, boolean compact, String filter, String search) {
        return getHistory(phone, limit, compact, filter, search, "");
    }

    // month: "yyyy-MM" to show only that month, or "" for everything
    public String getHistory(String phone, int limit, boolean compact, String filter, String search, String month) {
        String q = search.trim().toLowerCase();

        String sql = "SELECT sender, receiver, amount, time, status FROM transactions "
                + "WHERE (sender = ? OR (receiver = ? AND status = 'Completed'))";
        String[] args;
        if (month.isEmpty()) {
            args = new String[]{phone, phone};
        } else {
            sql = sql + " AND strftime('%Y-%m', time) = ?";
            args = new String[]{phone, phone, month};
        }

        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(sql + " ORDER BY id DESC", args);

        String text = "";
        int shown = 0; // the limit counts only the lines that pass the filter and the search
        while (c.moveToNext() && shown < limit) {
            String sender = c.getString(0);
            String receiver = c.getString(1);
            double amount = c.getDouble(2);
            String time = c.getString(3);
            String status = c.getString(4);

            String other;
            String type;
            if (sender.equals(phone)) {
                other = receiver;
                type = REJECTED.equals(status) ? "Rejected" : "Sent";
            } else {
                other = sender;
                type = "Received";
            }

            if (!filter.equals("All") && !filter.equals(type)) {
                continue;
            }
            String otherName = getName(other);
            if (!q.isEmpty() && !otherName.toLowerCase().contains(q) && !other.contains(q)) {
                continue;
            }

            String line;
            if (type.equals("Rejected")) {
                line = ctx.getString(R.string.hist_rejected, money(amount), otherName);
            } else if (type.equals("Sent")) {
                line = ctx.getString(R.string.hist_sent, money(amount), otherName);
            } else {
                line = ctx.getString(R.string.hist_received, money(amount), otherName);
            }

            if (compact) {
                text = text + line + "\n";
            } else {
                text = text + line + "\n" + time + "  |  " + statusLabel(status) + "\n\n";
            }
            shown = shown + 1;
        }
        c.close();

        if (text.isEmpty()) {
            if (!filter.equals("All") || !q.isEmpty()) {
                return ctx.getString(R.string.no_matching);
            }
            if (!month.isEmpty()) {
                return ctx.getString(R.string.no_tx_month);
            }
            return ctx.getString(R.string.no_transactions_yet);
        }
        return text.trim();
    }

    // money sent each day for the last `days` days (oldest first), used by the chart
    public double[] getDailySent(String phone, int days) {
        double[] result = new double[days];
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SQLiteDatabase db = getReadableDatabase();
        for (int i = 0; i < days; i++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, i - (days - 1));
            Cursor c = db.rawQuery("SELECT SUM(amount) FROM transactions "
                            + "WHERE sender = ? AND status = 'Completed' AND date(time) = ?",
                    new String[]{phone, format.format(day.getTime())});
            if (c.moveToFirst()) {
                result[i] = c.getDouble(0);
            }
            c.close();
        }
        return result;
    }

    public static String[] lastDays(int days) {
        String[] labels = new String[days];
        SimpleDateFormat format = new SimpleDateFormat(Lang.isArabic() ? "EEEEE" : "EEE", Lang.labelLocale());
        for (int i = 0; i < days; i++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, i - (days - 1));
            labels[i] = format.format(day.getTime());
        }
        return labels;
    }

    // "2026-11" style key for a month, matches strftime('%Y-%m', time)
    public static String monthKey(Calendar month) {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(month.getTime());
    }

    public double getSentInMonth(String phone, String month) {
        return sumMonth("sender", phone, month);
    }

    public double getReceivedInMonth(String phone, String month) {
        return sumMonth("receiver", phone, month);
    }

    private double sumMonth(String column, String phone, String month) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(amount) FROM transactions WHERE " + column
                        + " = ? AND status = 'Completed' AND strftime('%Y-%m', time) = ?",
                new String[]{phone, month});
        double total = 0;
        if (c.moveToFirst()) {
            total = c.getDouble(0);
        }
        c.close();
        return total;
    }

    public int getCountInMonth(String phone, String month) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM transactions "
                        + "WHERE (sender = ? OR receiver = ?) AND status = 'Completed' "
                        + "AND strftime('%Y-%m', time) = ?",
                new String[]{phone, phone, month});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    // money sent in each of the `months` months ending at `end` (oldest first), used by the chart
    public double[] getMonthlySent(String phone, Calendar end, int months) {
        double[] result = new double[months];
        for (int i = 0; i < months; i++) {
            Calendar m = (Calendar) end.clone();
            m.add(Calendar.MONTH, i - (months - 1));
            result[i] = getSentInMonth(phone, monthKey(m));
        }
        return result;
    }

    public static String[] monthLabels(Calendar end, int months) {
        String[] labels = new String[months];
        SimpleDateFormat format = new SimpleDateFormat("MMM", Lang.labelLocale());
        for (int i = 0; i < months; i++) {
            Calendar m = (Calendar) end.clone();
            m.add(Calendar.MONTH, i - (months - 1));
            labels[i] = format.format(m.getTime());
        }
        return labels;
    }

    // id of the newest completed transfer received by this phone (0 if none)
    public long getLastReceivedId(String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT MAX(id) FROM transactions "
                + "WHERE receiver = ? AND status = 'Completed'", new String[]{phone});
        long id = 0;
        if (c.moveToFirst()) {
            id = c.getLong(0);
        }
        c.close();
        return id;
    }

    // message about the transfers received after `afterId` ("" if there are none)
    public String getReceivedSince(String phone, long afterId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT sender, amount FROM transactions "
                        + "WHERE receiver = ? AND status = 'Completed' AND id > ? ORDER BY id",
                new String[]{phone, String.valueOf(afterId)});
        int count = 0;
        double total = 0;
        String lastSender = "";
        while (c.moveToNext()) {
            lastSender = c.getString(0);
            total = total + c.getDouble(1);
            count++;
        }
        c.close();

        if (count == 0) {
            return "";
        }
        if (count == 1) {
            return ctx.getString(R.string.received_from, money(total), getName(lastSender));
        }
        return ctx.getString(R.string.received_multiple, money(total), count);
    }

    public double getTotalSent(String phone) {
        return sum("SELECT SUM(amount) FROM transactions WHERE sender = ? AND status = 'Completed'", phone);
    }

    public double getTotalReceived(String phone) {
        return sum("SELECT SUM(amount) FROM transactions WHERE receiver = ? AND status = 'Completed'", phone);
    }

    private double sum(String sql, String phone) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(sql, new String[]{phone});
        double total = 0;
        if (c.moveToFirst()) {
            total = c.getDouble(0);
        }
        c.close();
        return total;
    }

    // the PIN is never saved as plain text, only its SHA-256 hash (salted with the phone)
    public static String hashPin(String phone, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((phone + ":" + pin).getBytes(StandardCharsets.UTF_8));
            String hex = "";
            for (byte b : bytes) {
                hex = hex + String.format("%02x", b);
            }
            return hex;
        } catch (NoSuchAlgorithmException e) {
            return pin;
        }
    }

    public static String normalize(String s) {
        String out = "";
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') {
                ch = (char) ('0' + (ch - '\u0660'));
            } else if (ch >= '\u06F0' && ch <= '\u06F9') {
                ch = (char) ('0' + (ch - '\u06F0'));
            } else if (ch == '\u066B' || ch == ',') {
                ch = '.';
            }
            out = out + ch;
        }
        return out;
    }

    public static String money(double value) {
        return String.format(Locale.US, "%.2f", value) + " " + currency;
    }
}
