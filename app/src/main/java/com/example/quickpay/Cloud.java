package com.example.quickpay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All the talking to the online database (Supabase) is here.
 *
 * The app never touches the tables. It only calls functions (see supabase.sql):
 *   sign_up, sign_in, sign_out, get_state, check_receiver, transfer, record_rejected,
 *   check_pin, change_pin, delete_account
 * The PIN is checked and the money is moved INSIDE the server, so a modified app cannot cheat.
 * After sign_in the server gives a session token, and every other call must send it.
 *
 * The local SQLite database (DatabaseHelper) is only a fast copy of this data.
 * startSync() asks the server for the newest data every few seconds and refreshes that copy.
 *
 * Every Done / answer runs on the main (UI) thread, so screens can use it directly.
 */
public class Cloud {
    public interface Done {
        void run(String result); // OK or the message to show to the user
    }

    public static final String OK = "OK";
    public static final String WRONG = "WRONG"; // checkPin: the PIN does not match

    private static final int POLL_MS = 5000;

    // one worker: the requests run one after the other, so an old answer never hides a newer one
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    // ---- sync state ----
    private static Context appCtx;
    private static String syncPhone = "";
    private static boolean polling;
    private static int generation;
    private static Runnable onChange;

    // ---------------- talking to the server ----------------

    private interface Reply {
        void run(JSONObject json, Exception error);
    }

    private static class ServerError extends IOException {
        final int code;

        ServerError(int code, String body) {
            super("HTTP " + code + ": " + body);
            this.code = code;
        }
    }

    private static void rpc(String function, JSONObject args, Reply reply) {
        IO.execute(() -> {
            JSONObject json = null;
            Exception error = null;
            try {
                json = call(function, args);
            } catch (Exception e) {
                Log.e("Cloud", function + " failed", e);
                error = e;
            }
            final JSONObject j = json;
            final Exception er = error;
            UI.post(() -> reply.run(j, er));
        });
    }

    private static JSONObject call(String function, JSONObject args) throws Exception {
        URL url = new URL(SupabaseConfig.URL + "/rest/v1/rpc/" + function);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("apikey", SupabaseConfig.KEY);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(args.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = read(is);
            if (code >= 400) {
                throw new ServerError(code, text);
            }
            return new JSONObject(text);
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        try (InputStream in = is) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static JSONObject args(Object... keyValue) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < keyValue.length; i += 2) {
                o.put((String) keyValue[i], keyValue[i + 1]);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return o;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("cashwing", Context.MODE_PRIVATE);
    }

    private static String token(Context ctx, String phone) {
        return prefs(ctx).getString("token_" + phone, "");
    }

    // the message for a call that could not reach the server or that the server refused
    private static String failure(Context ctx, Exception e) {
        if (e instanceof ServerError) {
            return ctx.getString(R.string.err_server) + " (" + ((ServerError) e).code + ")";
        }
        return ctx.getString(R.string.err_network);
    }

    // the message for an error code that every call can get
    private static String common(Context ctx, String code) {
        if (code.equals("AUTH")) {
            return ctx.getString(R.string.err_session);
        }
        if (code.equals("LOCKED")) {
            return ctx.getString(R.string.err_locked);
        }
        return ctx.getString(R.string.err_server) + " (" + code + ")";
    }

    // ---------------- account ----------------

    public static void createUser(Context ctx, String name, String phone, String pin, Done done) {
        rpc("sign_up", args("p_name", name, "p_phone", phone, "p_pin", pin), (j, e) -> {
            if (e != null) {
                done.run(failure(ctx, e));
                return;
            }
            String err = j.optString("error", "");
            if (err.isEmpty()) {
                DatabaseHelper.writer(ctx).saveUser(phone, name, 20000, true);
                done.run(OK);
            } else if (err.equals("TAKEN")) {
                done.run(ctx.getString(R.string.err_phone_registered));
            } else if (err.equals("BAD_PHONE")) {
                done.run(ctx.getString(R.string.err_phone_mobile));
            } else if (err.equals("BAD_PIN")) {
                done.run(ctx.getString(R.string.err_pin_range));
            } else {
                done.run(common(ctx, err));
            }
        });
    }

    // Login with a limit: after 3 wrong PINs the account is locked for 5 minutes (the server counts).
    public static void login(Context ctx, String phone, String pin, Done done) {
        rpc("sign_in", args("p_phone", phone, "p_pin", pin), (j, e) -> {
            if (e != null) {
                done.run(failure(ctx, e));
                return;
            }
            String err = j.optString("error", "");
            if (err.isEmpty()) {
                prefs(ctx).edit().putString("token_" + phone, j.optString("token", "")).apply();
                DatabaseHelper.writer(ctx).saveUser(phone, j.optString("name", ""),
                        j.optDouble("balance", 0), true);
                done.run(OK);
            } else if (err.equals("WRONG")) {
                done.run(ctx.getString(R.string.err_wrong_attempts, j.optInt("left", 0)));
            } else if (err.equals("NOT_FOUND")) {
                done.run(ctx.getString(R.string.err_wrong_phone_pin));
            } else {
                done.run(common(ctx, err));
            }
        });
    }

    // forgets the session on this phone and on the server
    public static void logout(Context ctx, String phone) {
        String t = token(ctx, phone);
        prefs(ctx).edit().remove("token_" + phone).apply();
        stopSync();
        if (!t.isEmpty()) {
            rpc("sign_out", args("p_phone", phone, "p_token", t), (j, e) -> { });
        }
    }

    // OK if the PIN is right, WRONG if not, or an error message
    public static void checkPin(Context ctx, String phone, String pin, Done done) {
        rpc("check_pin", args("p_phone", phone, "p_token", token(ctx, phone), "p_pin", pin), (j, e) -> {
            if (e != null) {
                done.run(failure(ctx, e));
                return;
            }
            String err = j.optString("error", "");
            if (err.isEmpty()) {
                done.run(OK);
            } else if (err.equals("WRONG")) {
                done.run(WRONG);
            } else {
                done.run(common(ctx, err));
            }
        });
    }

    public static void changePin(Context ctx, String phone, String oldPin, String newPin, Done done) {
        rpc("change_pin", args("p_phone", phone, "p_token", token(ctx, phone),
                "p_old_pin", oldPin, "p_new_pin", newPin), (j, e) -> {
            if (e != null) {
                done.run(failure(ctx, e));
                return;
            }
            String err = j.optString("error", "");
            if (err.isEmpty()) {
                done.run(OK);
            } else if (err.equals("WRONG")) {
                done.run(ctx.getString(R.string.err_wrong_current_pin));
            } else if (err.equals("BAD_PIN")) {
                done.run(ctx.getString(R.string.err_pin_range));
            } else {
                done.run(common(ctx, err));
            }
        });
    }

    // The account is switched off, but its transactions stay, so the other users
    // keep a correct history (they see "Deleted account").
    public static void deleteUser(Context ctx, String phone, String pin, Done done) {
        rpc("delete_account", args("p_phone", phone, "p_token", token(ctx, phone), "p_pin", pin), (j, e) -> {
            if (e != null) {
                done.run(failure(ctx, e));
                return;
            }
            String err = j.optString("error", "");
            if (err.isEmpty()) {
                DatabaseHelper.writer(ctx).deactivate(phone);
                prefs(ctx).edit().remove("token_" + phone).apply();
                done.run(OK);
            } else if (err.equals("WRONG")) {
                done.run(ctx.getString(R.string.err_wrong_pin));
            } else {
                done.run(common(ctx, err));
            }
        });
    }

    // ---------------- money ----------------

    // OK if the receiver can get money (the charity fund always can)
    public static void checkReceiver(Context ctx, String receiver, Done done) {
        if (Charities.isCharity(receiver)) {
            done.run(OK);
            return;
        }
        String phone = syncPhone;
        if (phone.isEmpty()) {
            done.run(ctx.getString(R.string.err_session));
            return;
        }
        rpc("check_receiver", args("p_phone", phone, "p_token", token(ctx, phone),
                "p_receiver", receiver), (j, e) -> {
            if (e != null) {
                done.run(failure(ctx, e));
                return;
            }
            String err = j.optString("error", "");
            if (err.isEmpty()) {
                DatabaseHelper.writer(ctx).saveOtherUser(receiver, j.optString("name", ""), true);
                done.run(OK);
            } else if (err.equals("NOT_FOUND")) {
                done.run(ctx.getString(R.string.err_receiver_not_found));
            } else {
                done.run(common(ctx, err));
            }
        });
    }

    // all the steps happen together or none of them happens (one database transaction in the server)
    public static void transfer(Context ctx, String sender, String receiver, double amount, Done done) {
        rpc("transfer", args("p_phone", sender, "p_token", token(ctx, sender),
                "p_receiver", receiver, "p_amount", amount), (j, e) -> {
            if (e != null) {
                done.run(failure(ctx, e));
                return;
            }
            DatabaseHelper local = DatabaseHelper.writer(ctx);
            String err = j.optString("error", "");
            long ts = j.optLong("ts", System.currentTimeMillis());
            if (err.isEmpty()) {
                local.setBalance(sender, j.optDouble("balance", local.getBalance(sender) - amount));
                local.saveTransaction(ts, sender, receiver, amount, DatabaseHelper.COMPLETED);
                done.run(OK);
            } else if (err.equals("NO_MONEY")) {
                local.saveTransaction(ts, sender, receiver, amount, DatabaseHelper.REJECTED);
                done.run(ctx.getString(R.string.err_insufficient_balance));
            } else if (err.equals("SELF")) {
                done.run(ctx.getString(R.string.err_self_transfer));
            } else if (err.equals("NO_SENDER") || err.equals("NO_RECEIVER")) {
                done.run(ctx.getString(R.string.err_receiver_not_found));
            } else if (err.equals("BAD_AMOUNT")) {
                done.run(ctx.getString(R.string.err_amount_zero));
            } else {
                done.run(common(ctx, err));
            }
        });
    }

    // a transfer that was refused (not enough money) is still saved in the history
    public static void recordRejected(Context ctx, String sender, String receiver, double amount) {
        rpc("record_rejected", args("p_phone", sender, "p_token", token(ctx, sender),
                "p_receiver", receiver, "p_amount", amount), (j, e) -> {
            if (e == null && j.optString("error", "").isEmpty()) {
                DatabaseHelper.writer(ctx).saveTransaction(j.optLong("ts", System.currentTimeMillis()),
                        sender, receiver, amount, DatabaseHelper.REJECTED);
            }
        });
    }

    // ---------------- sync: server -> local copy ----------------

    // the screens that show data set this to refresh themselves when new data arrives
    public static void setOnChange(Runnable r) {
        onChange = r;
    }

    private static void changed() {
        if (onChange != null) {
            onChange.run();
        }
    }

    // Starts copying the data of this user from the server into the local database.
    // Safe to call many times: it does nothing if it is already running for this phone.
    public static void startSync(Context ctx, String phone) {
        if (polling && phone.equals(syncPhone)) {
            return;
        }
        stopSync();
        appCtx = ctx.getApplicationContext();
        syncPhone = phone;
        polling = true;
        poll(generation);
    }

    public static void stopSync() {
        polling = false;
        generation++;
        syncPhone = "";
        onChange = null;
    }

    private static void poll(int gen) {
        if (!polling || gen != generation) {
            return;
        }
        Context ctx = appCtx;
        String phone = syncPhone;
        rpc("get_state", args("p_phone", phone, "p_token", token(ctx, phone)), (j, e) -> {
            if (!polling || gen != generation) {
                return;
            }
            if (e == null) {
                if (j.optString("error", "").isEmpty()) {
                    applyState(ctx, phone, j);
                } else if (j.optString("error", "").equals("AUTH")) {
                    return; // the session ended: stop asking
                }
            }
            UI.postDelayed(() -> poll(gen), POLL_MS);
        });
    }

    private static void applyState(Context ctx, String phone, JSONObject j) {
        DatabaseHelper local = DatabaseHelper.writer(ctx);
        local.saveUser(phone, j.optString("name", ""), j.optDouble("balance", 0), j.optBoolean("active", true));

        // we need the names of the other people to show the history
        JSONArray people = j.optJSONArray("people");
        if (people != null) {
            for (int i = 0; i < people.length(); i++) {
                JSONObject p = people.optJSONObject(i);
                if (p == null) {
                    continue;
                }
                String other = p.optString("phone", "");
                if (other.isEmpty() || Charities.isCharity(other)) {
                    continue;
                }
                local.saveOtherUser(other, p.optString("name", ""), p.optBoolean("active", true));
            }
        }

        JSONArray txs = j.optJSONArray("transactions");
        if (txs != null) {
            SharedPreferences prefs = prefs(ctx);
            if (!prefs.contains("seen_" + phone)) {
                // first time on this phone: the old transfers are not "new money"
                long newest = 0;
                for (int i = 0; i < txs.length(); i++) {
                    JSONObject t = txs.optJSONObject(i);
                    if (t != null && phone.equals(t.optString("receiver", ""))
                            && DatabaseHelper.COMPLETED.equals(t.optString("status", ""))) {
                        newest = Math.max(newest, t.optLong("ts", 0));
                    }
                }
                prefs.edit().putLong("seen_" + phone, newest).apply();
            }
            for (int i = 0; i < txs.length(); i++) {
                JSONObject t = txs.optJSONObject(i);
                if (t == null) {
                    continue;
                }
                long ts = t.optLong("ts", 0);
                String sender = t.optString("sender", "");
                String receiver = t.optString("receiver", "");
                if (ts == 0 || sender.isEmpty() || receiver.isEmpty()) {
                    continue;
                }
                local.saveTransaction(ts, sender, receiver, t.optDouble("amount", 0), t.optString("status", ""));
            }
        }
        changed();
    }
}
