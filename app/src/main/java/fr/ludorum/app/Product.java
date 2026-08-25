package fr.ludorum.app;

import org.json.JSONArray;
import org.json.JSONObject;

final class Product {
    int id;
    String name = "";
    String slug = "";
    String shortDescription = "";
    String type = "simple";
    String permalink = "";
    String imageUrl = "";
    String currentPrice = "";
    String regularPrice = "";
    String currencyCode = "EUR";
    String currencySymbol = "€";
    int currencyMinorUnit = 2;
    boolean onSale;
    boolean inStock;
    boolean purchasable;
    String addToCartUrl = "";

    static Product fromJson(JSONObject o) {
        Product p = new Product();
        p.id = o.optInt("id");
        p.name = o.optString("name", "Produit");
        p.slug = o.optString("slug", "");
        p.shortDescription = o.optString("short_description", "");
        p.type = o.optString("type", "simple");
        p.permalink = o.optString("permalink", "");
        p.onSale = o.optBoolean("on_sale", false);
        p.inStock = o.optBoolean("is_in_stock", false);
        p.purchasable = o.optBoolean("is_purchasable", false);

        JSONObject prices = o.optJSONObject("prices");
        if (prices != null) {
            p.currentPrice = prices.optString("price", "");
            p.regularPrice = prices.optString("regular_price", "");
            p.currencyCode = prices.optString("currency_code", "EUR");
            p.currencySymbol = prices.optString("currency_symbol", "€");
            p.currencyMinorUnit = prices.optInt("currency_minor_unit", 2);
        }

        JSONArray imgs = o.optJSONArray("images");
        if (imgs != null && imgs.length() > 0) {
            JSONObject img = imgs.optJSONObject(0);
            if (img != null) {
                p.imageUrl = img.optString("thumbnail", img.optString("src", ""));
            }
        }

        JSONObject add = o.optJSONObject("add_to_cart");
        if (add != null) p.addToCartUrl = add.optString("url", "");
        return p;
    }
}
