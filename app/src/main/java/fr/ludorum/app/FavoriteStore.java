package fr.ludorum.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class FavoriteStore {
    private static final String PREFS = "ludorum_native_wishlist";
    private static final String KEY = "products";

    private final SharedPreferences preferences;

    FavoriteStore(Context context) {
        preferences = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    synchronized boolean contains(int productId) {
        for (Product product : getAll()) {
            if (product.id == productId) return true;
        }
        return false;
    }

    synchronized boolean toggle(Product product) {
        List<Product> products = getAll();

        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).id == product.id) {
                products.remove(i);
                save(products);
                return false;
            }
        }

        // Le dernier favori ajouté apparaît en premier.
        products.add(0, cloneProduct(product));
        save(products);
        return true;
    }

    synchronized List<Product> getAll() {
        List<Product> result = new ArrayList<>();

        String raw = preferences.getString(KEY, "[]");

        try {
            JSONArray array = new JSONArray(raw);

            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;

                Product p = new Product();
                p.id = o.optInt("id");
                p.name = o.optString("name", "Produit");
                p.type = o.optString("type", "simple");
                p.permalink = o.optString("permalink", "");
                p.imageUrl = o.optString("imageUrl", "");
                p.currentPrice = o.optString("currentPrice", "");
                p.regularPrice = o.optString("regularPrice", "");
                p.currencyCode = o.optString("currencyCode", "EUR");
                p.currencySymbol = o.optString("currencySymbol", "€");
                p.currencyMinorUnit = o.optInt("currencyMinorUnit", 2);
                p.onSale = o.optBoolean("onSale", false);
                p.inStock = o.optBoolean("inStock", false);
                p.purchasable = o.optBoolean("purchasable", false);
                p.addToCartUrl = o.optString("addToCartUrl", "");

                if (p.id > 0) result.add(p);
            }
        } catch (Exception ignored) {
            // En cas de préférence corrompue, la wishlist repart vide
            // au lieu de faire planter l'application.
        }

        return result;
    }

    private void save(List<Product> products) {
        JSONArray array = new JSONArray();

        for (Product p : products) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("type", p.type);
                o.put("permalink", p.permalink);
                o.put("imageUrl", p.imageUrl);
                o.put("currentPrice", p.currentPrice);
                o.put("regularPrice", p.regularPrice);
                o.put("currencyCode", p.currencyCode);
                o.put("currencySymbol", p.currencySymbol);
                o.put("currencyMinorUnit", p.currencyMinorUnit);
                o.put("onSale", p.onSale);
                o.put("inStock", p.inStock);
                o.put("purchasable", p.purchasable);
                o.put("addToCartUrl", p.addToCartUrl);
                array.put(o);
            } catch (Exception ignored) {}
        }

        preferences.edit()
                .putString(KEY, array.toString())
                .apply();
    }

    private Product cloneProduct(Product source) {
        Product p = new Product();
        p.id = source.id;
        p.name = source.name;
        p.type = source.type;
        p.permalink = source.permalink;
        p.imageUrl = source.imageUrl;
        p.currentPrice = source.currentPrice;
        p.regularPrice = source.regularPrice;
        p.currencyCode = source.currencyCode;
        p.currencySymbol = source.currencySymbol;
        p.currencyMinorUnit = source.currencyMinorUnit;
        p.onSale = source.onSale;
        p.inStock = source.inStock;
        p.purchasable = source.purchasable;
        p.addToCartUrl = source.addToCartUrl;
        return p;
    }
}
