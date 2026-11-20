package com.example.quickpay;

/**
 * Where the online database lives (Supabase). The "publishable" key is made to be inside an app:
 * it cannot read or change any table by itself, it can only call the functions made by supabase.sql.
 */
public class SupabaseConfig {
    public static final String URL = "https://sbjdtixzkodvhejedmhp.supabase.co";
    public static final String KEY = "sb_publishable_yS4zlNiaEqZHBly-ZPtEBw_v_GNZi6z";
}
