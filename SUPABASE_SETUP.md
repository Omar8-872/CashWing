# Supabase setup (do once)

1. Supabase -> your project -> SQL Editor -> New query.
2. Paste ALL of `supabase.sql` and press Run. (Safe to run again.)
3. Run the app. Create account -> Login. No SMS, no SHA, no Firebase.

The app settings (URL + publishable key) are in `SupabaseConfig.java`.
The tables are locked: the app can only call the functions in `supabase.sql`.

If something fails the message ends with a code:
- (404): `supabase.sql` was not run (or was not run completely). Run it again.
- (401) / (403): the key in `SupabaseConfig.java` is wrong.
- "Connection problem": the emulator/phone has no internet.

To add a charity: add a line in `Charities.java`, its texts in strings.xml (en + ar), and one row in the `insert into public.users` list in `supabase.sql`.
