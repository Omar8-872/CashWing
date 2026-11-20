package com.example.quickpay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The list of charities (hospitals, orphans, ...).
 * Every charity is a "user" in the server (table users) with a special phone like 00000000001.
 * It is created by supabase.sql, so also add a line there for a new charity.
 * To add a new charity: add one line to ALL below + its name and description in strings.xml (en and ar).
 */
public class Charities {
    public static final int HOSPITALS = 0;
    public static final int ORPHANS = 1;
    public static final int MORE = 2;

    public static class Item {
        public final String id;          // phone number used on the server
        public final int category;
        public final int nameRes;
        public final int descRes;
        public final int iconRes;
        public final String englishName; // name of the charity in supabase.sql

        Item(String id, int category, int nameRes, int descRes, int iconRes, String englishName) {
            this.id = id;
            this.category = category;
            this.nameRes = nameRes;
            this.descRes = descRes;
            this.iconRes = iconRes;
            this.englishName = englishName;
        }
    }

    public static final List<Item> ALL = Arrays.asList(
            new Item("00000000001", HOSPITALS, R.string.ch_hope_name, R.string.ch_hope_desc,
                    R.drawable.ic_cat_hospital, "Hope Children's Hospital"),
            new Item("00000000002", HOSPITALS, R.string.ch_heart_name, R.string.ch_heart_desc,
                    R.drawable.ic_cat_heart, "Heart Care Fund"),
            new Item("00000000003", HOSPITALS, R.string.ch_cancer_name, R.string.ch_cancer_desc,
                    R.drawable.ic_cat_hospital, "Cancer Patients Support"),

            new Item("00000000004", ORPHANS, R.string.ch_home_name, R.string.ch_home_desc,
                    R.drawable.ic_cat_orphan, "Orphans Care Home"),
            new Item("00000000005", ORPHANS, R.string.ch_sponsor_name, R.string.ch_sponsor_desc,
                    R.drawable.ic_cat_heart, "Orphan Sponsorship"),

            new Item("00000000006", MORE, R.string.ch_food_name, R.string.ch_food_desc,
                    R.drawable.ic_cat_food, "Food for the Needy"),
            new Item("00000000007", MORE, R.string.ch_water_name, R.string.ch_water_desc,
                    R.drawable.ic_cat_water, "Clean Water"),
            new Item("00000000008", MORE, R.string.ch_edu_name, R.string.ch_edu_desc,
                    R.drawable.ic_cat_edu, "Education Support"),
            new Item("00000000009", MORE, R.string.ch_elder_name, R.string.ch_elder_desc,
                    R.drawable.ic_cat_group, "Elderly Care"),
            new Item(DatabaseHelper.CHARITY_PHONE, MORE, R.string.charity_fund, R.string.ch_general_desc,
                    R.drawable.ic_charity, DatabaseHelper.CHARITY_NAME)
    );

    public static Item find(String phone) {
        for (Item item : ALL) {
            if (item.id.equals(phone)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isCharity(String phone) {
        return find(phone) != null;
    }

    public static int iconOf(String phone) {
        Item item = find(phone);
        return item == null ? R.drawable.ic_charity : item.iconRes;
    }

    public static List<Item> inCategory(int category) {
        List<Item> list = new ArrayList<>();
        for (Item item : ALL) {
            if (item.category == category) {
                list.add(item);
            }
        }
        return list;
    }
}
