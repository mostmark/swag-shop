package com.demo.shop.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductCatalog {

    private final List<Product> products = List.of(
            product("red-fedora", "Classic Red Fedora", "Headwear", "49.00",
                    "A wool felt fedora in signature red with a black grosgrain band. Stiff brim, pinched crown and a comfortable inner sweatband for long conference days.",
                    "hat", "fedora", "wool", "red"),
            product("black-coffee-mug", "Matte Black Coffee Mug", "Drinkware", "16.00",
                    "A heavy stoneware mug with a matte black glaze. Holds a generous double espresso or a large filter coffee for those long debugging sessions.",
                    "mug", "coffee", "cup", "ceramic", "black"),
            product("red-t-shirt", "Red Crew Neck T-Shirt", "Apparel", "24.00",
                    "A soft organic cotton crew neck shirt in bright red. Regular fit, preshrunk fabric and reinforced seams that survive many washes.",
                    "tshirt", "shirt", "cotton", "apparel", "red"),
            product("charcoal-zip-hoodie", "Charcoal Zip Hoodie", "Apparel", "59.00",
                    "A brushed fleece hoodie in charcoal with a full length zipper, drawstring hood and two front pockets. Warm enough for chilly data centers.",
                    "hoodie", "sweater", "fleece", "zipper", "charcoal"),
            product("red-water-bottle", "Insulated Water Bottle", "Drinkware", "29.00",
                    "A double walled stainless steel bottle with a red powder coat finish. Keeps drinks cold for a full day and hot for twelve hours.",
                    "bottle", "water", "steel", "insulated", "red"),
            product("laptop-backpack", "Commuter Laptop Backpack", "Bags", "89.00",
                    "A water resistant backpack with a padded compartment for laptops up to sixteen inches, quick access pockets and a hidden passport pocket.",
                    "backpack", "bag", "laptop", "travel", "black"),
            product("sticker-pack", "Developer Sticker Pack", "Accessories", "8.00",
                    "Twelve durable vinyl stickers with terminal prompts, curly braces, clouds and geometric shapes. Perfect for decorating laptops and notebooks.",
                    "stickers", "vinyl", "laptop", "decoration"),
            product("enamel-pin-set", "Enamel Pin Set", "Accessories", "14.00",
                    "Four hard enamel pins with polished metal edges: a fedora, a coffee cup, a cloud and a gear. Comes with locking pin backs.",
                    "pins", "enamel", "badge", "collectible"));

    public List<Product> all() {
        return products;
    }

    public Optional<Product> findById(String id) {
        return products.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    private static Product product(String id, String name, String category, String price, String description,
            String... tags) {
        return new Product(id, name, category, description, new BigDecimal(price), "/img/products/" + id + ".webp",
                List.of(tags));
    }
}
