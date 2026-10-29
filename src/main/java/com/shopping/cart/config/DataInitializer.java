package com.shopping.cart.config;

import com.shopping.cart.entity.Product;
import com.shopping.cart.entity.ProductImage;
import com.shopping.cart.entity.Role;
import com.shopping.cart.entity.User;
import com.shopping.cart.repository.ProductRepository;
import com.shopping.cart.repository.RoleRepository;
import com.shopping.cart.repository.UserRepository;
import com.shopping.cart.utility.PasswordHashingUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.admin.seed-username:}")
    private String adminSeedUsername;

    @Value("${app.admin.seed-password:}")
    private String adminSeedPassword;

    public DataInitializer(
            RoleRepository roleRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            PlatformTransactionManager transactionManager) {
        this.roleRepository = roleRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("DataInitializer starting");
        // Commit each seed step separately so one failure cannot roll back new SKUs
        // (important with Neon pooler + long startup transactions).
        runInTransaction(() -> seedRole("User"));
        runInTransaction(() -> seedRole("Admin"));
        seedSampleProducts();
        backfillMissingBrandCategory();
        runInTransaction(this::seedAdminUser);
        log.info("DataInitializer finished");
    }

    /** Ensures brand/category are filled even if an earlier seed path skipped them. */
    private void backfillMissingBrandCategory() {
        runInTransaction(() -> {
            int updated = 0;
            for (Product product : productRepository.findAll()) {
                if (product.isDeleted()) continue;
                boolean dirty = false;
                if (product.getBrand() == null || product.getBrand().isBlank()) {
                    product.setBrand(inferBrand(product.getName()));
                    dirty = true;
                }
                if (product.getCategory() == null || product.getCategory().isBlank()) {
                    product.setCategory(inferCategory(product.getName()));
                    dirty = true;
                }
                if (dirty) {
                    productRepository.save(product);
                    updated++;
                    log.info("Backfilled {} -> {} / {}",
                            product.getName(), product.getBrand(), product.getCategory());
                }
            }
            log.info("Brand/category backfill updated {} products", updated);
        });
    }

    private static String inferBrand(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.contains("iphone") || n.contains("ipad") || n.contains("macbook")
                || n.contains("airpods") || n.contains("apple watch")) {
            return "Apple";
        }
        if (n.contains("galaxy") || n.contains("samsung")) return "Samsung";
        if (n.contains("sony") || n.contains("wh-1000")) return "Sony";
        if (n.contains("asus") || n.contains("rog")) return "ASUS";
        return "Other";
    }

    private static String inferCategory(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.contains("iphone") || n.contains("galaxy s")) return "Smartphones";
        if (n.contains("ipad") || n.contains("tab ")) return "Tablets";
        if (n.contains("macbook") || n.contains("rog") || n.contains("laptop")) return "Laptops";
        if (n.contains("airpods") || n.contains("wh-1000") || n.contains("headphone")) {
            return "Audio / Headphones";
        }
        if (n.contains("watch")) return "Smartwatches / Wearables";
        return "Accessories";
    }

    private void runInTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private void seedRole(String name) {
        if (roleRepository.findByName(name) == null) {
            roleRepository.save(new Role(name));
        }
    }

    private void seedSampleProducts() {
        retireLegacyDemoProducts();

        // Prices in MMK — aligned with pixel_tech_web catalog. Stripe converts MMK→SGD.
        seedProduct("iPhone 16 Pro — 256GB",
                "Apple A18 Pro, 6.3\" Super Retina XDR, Pro camera system with 5x Telephoto. Titanium design in Desert Titanium.",
                "Apple", "Smartphones", "4899000.00", 18,
                "https://images.unsplash.com/photo-1695048133142-1a20484d2569?auto=format&fit=crop&w=600&q=80");
        seedProduct("iPhone 16 — 128GB",
                "A18 chip, Camera Control, longer battery life, and Action Button. Everyday flagship in Ultramarine.",
                "Apple", "Smartphones", "3299000.00", 28,
                "https://images.unsplash.com/photo-1592899677977-9c10ca588bbd?auto=format&fit=crop&w=600&q=80");
        seedProduct("iPhone 17 Pro Max — 256GB",
                "Largest Pro display, advanced camera stack, and all-day battery. Latest Apple flagship for creators and power users.",
                "Apple", "Smartphones", "5899000.00", 12,
                "https://images.unsplash.com/photo-1759820940611-facb87e629d8?auto=format&fit=crop&w=600&q=80");
        seedProduct("iPhone 18 Pro Max — 256GB",
                "Next-gen Pro Max with advanced camera system, larger Super Retina XDR display, and all-day battery. Flagship titanium design.",
                "Apple", "Smartphones", "6499000.00", 10,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240448/pixel-tech/products/iphone-18-pro-model-unselect-gallery-1-202609-680eee70-16a6-4f59-9f14-751af72ab9c6.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240449/pixel-tech/products/iphone-18-pro-model-unselect-gallery-2-202609-b56353a7-a1bf-4c7b-9db2-a2c9c2a0d241.jpg"
                ));
        seedProduct("iPhone Duo — 256GB",
                "Book-style foldable iPhone with dual finishes (Starwhite / Nightsky), expansive inner display, cover screen, and dual-camera system.",
                "Apple", "Smartphones", "7999000.00", 8,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240450/pixel-tech/products/iphone-duo-finish-unselect-gallery-2-202609-a5d00f92-2dee-49fb-8a88-01bdd19bd854.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240450/pixel-tech/products/iphone-duo-finish-select-202609-starwhite-44c1b38b-d407-4431-b9b9-a96da108fa84.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240451/pixel-tech/products/iphone-duo-finish-select-202609-starwhite_AV1-10ede2ff-c9b3-4bb4-948a-456fb51800d2.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240452/pixel-tech/products/iphone-duo-finish-select-202609-starwhite_AV2-dbf6ac56-1195-49ac-81f3-55eabf22145d.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240452/pixel-tech/products/iphone-duo-finish-select-202609-starwhite_AV3-599910c3-3c7a-4ef3-bd9a-14e4dc3e3b1a.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240453/pixel-tech/products/iphone-duo-finish-select-202609-starwhite_AV4_GEO_-85ed6898-a0c7-486e-a4e0-eccafb185100.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240454/pixel-tech/products/iphone-duo-finish-select-202609-nightsky-78efc62a-d958-479d-9c04-dcb30dba60e6.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240454/pixel-tech/products/iphone-duo-finish-select-202609-nightsky_AV2-695dfe65-9705-4322-8873-64c3f3ab759f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240455/pixel-tech/products/iphone-duo-finish-select-202609-nightsky_AV3-668fd0e7-8ade-43bc-bf34-68a38b1d1ce4.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793240456/pixel-tech/products/iphone-duo-finish-select-202609-nightsky_AV4_GEO_S-779dc215-a70c-4bdb-8d37-cd078c00d00f.jpg"
                ));
        seedProduct("Samsung Galaxy S25 Ultra — 256GB",
                "Dynamic AMOLED 2X, S Pen, and pro-grade zoom cameras. Android flagship with DeX and long battery life.",
                "Samsung", "Smartphones", "4599000.00", 16,
                "https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?auto=format&fit=crop&w=600&q=80");
        seedProduct("MacBook Air 13\" M3 — 16GB/512GB",
                "Fanless M3 performance, Liquid Retina display, MagSafe, and all-day battery. Ideal for students and light creators.",
                "Apple", "Laptops", "4299000.00", 14,
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=600&q=80");
        seedProduct("MacBook Pro 14\" M4 Pro — 24GB/512GB",
                "Liquid Retina XDR, pro ports, and sustained performance for code, video, and 3D. Space Black finish.",
                "Apple", "Laptops", "7899000.00", 8,
                "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=600&q=80");
        seedProduct("ASUS ROG Strix G16",
                "High-refresh gaming laptop with discrete RTX GPU, RGB keyboard, and advanced cooling for esports and creators.",
                "ASUS", "Laptops", "5499000.00", 9,
                "https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=600&q=80");
        seedProduct("AirPods Pro 2 (USB-C)",
                "Active Noise Cancellation, Adaptive Audio, Spatial Audio, and MagSafe charging case with USB-C.",
                "Apple", "Audio / Headphones", "899000.00", 40,
                "https://images.unsplash.com/photo-1600294037681-c80b4cb5b434?auto=format&fit=crop&w=600&q=80");
        seedProduct("Apple Watch Series 10 — 46mm",
                "Thinner aluminum case, brighter Always-On Retina, sleep apnea notifications, and all-day battery.",
                "Apple", "Smartwatches / Wearables", "1599000.00", 22,
                "https://images.unsplash.com/photo-1434493789847-2f02dc6ca35d?auto=format&fit=crop&w=600&q=80");
        seedProduct("iPad Pro 11\" M4 — 256GB",
                "Ultra Retina XDR, Apple Pencil Pro support, and desktop-class M4 performance in a thin tablet.",
                "Apple", "Tablets", "3899000.00", 11,
                "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=600&q=80");
        seedProduct("Sony WH-1000XM5",
                "Industry-leading noise cancelling headphones with 30-hour battery and multipoint Bluetooth.",
                "Sony", "Audio / Headphones", "1299000.00", 20,
                "https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?auto=format&fit=crop&w=600&q=80");
        seedProduct("Samsung Galaxy Tab S9 — 128GB",
                "AMOLED tablet with included S Pen, DeX mode, and IP68 durability for work and media.",
                "Samsung", "Tablets", "2199000.00", 14,
                "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?auto=format&fit=crop&w=600&q=80");
    }

    /** Soft-delete older fictional demo SKUs + leftover Aura Fresh grocery rows. */
    private void retireLegacyDemoProducts() {
        List<String> legacy = List.of(
                "Pixel Pro 15 — 256GB",
                "NovaBook Air 14\"",
                "ThunderBook Gaming 16\"",
                "Pulse Buds Pro",
                "Orbit Watch SE",
                "Volt Power Bank 20,000mAh",
                "FramePad 11\" Tablet",
                "Crystal 4K Webcam",
                "Aero Mechanical Keyboard",
                "Summit Phone Lite",
                "Studio Monitor 27\" 2K",
                "Portable SSD 1TB",
                // Grocery leftovers from the shared Neon DB (Aura Fresh era)
                "Ayeyarwady Cavendish Bananas",
                "Coastal Tiger Prawns",
                "Cold-Pressed Peanut Oil",
                "Crispy Butter Cookies",
                "Dried Chickpeas",
                "Dried Rice Vermicelli Noodles",
                "Farm-Fresh Free-Range Eggs",
                "Fresh Green Cabbage",
                "Fresh Organic Baby Spinach",
                "Fresh River Fish Fillet",
                "Fresh Shan State Organic Avocados",
                "Fresh-Pressed Lime Juice",
                "Gluten-Free Almond & Seed Bread",
                "Halal Free-Range Whole Chicken",
                "Highland Green Tea Leaves",
                "Hot Bird's Eye Chilies",
                "Lean Pork Shoulder",
                "Natural Organic Coconut Water",
                "Natural Plain Yogurt",
                "Organic Cashew & Almond Granola",
                "Organic Farm-Fresh Grass-Fed Milk",
                "Organic Fresh Ginger Root",
                "Premium Myanmar Raw Honey",
                "Premium Shan Hills Arabica Coffee Beans",
                "Seasonal Myanmar Mangoes",
                "Shwe Bo Paw San Premium Rice",
                "Traditional Fish Sauce",
                "Traditional Shan Yellow Tofu",
                "Vine-Ripened Inle Tomatoes",
                "Warm Butter Naan Flatbread"
        );
        runInTransaction(() -> {
            int retired = 0;
            for (String name : legacy) {
                var match = productRepository.findByNameIgnoreCase(name);
                if (match.isPresent() && !match.get().isDeleted()) {
                    Product product = match.get();
                    product.setDeleted(true);
                    productRepository.save(product);
                    retired++;
                    log.info("Retired legacy product: {}", name);
                }
            }
            log.info("Legacy product retirement complete ({} soft-deleted)", retired);
        });
    }

    private void seedProduct(
            String name,
            String description,
            String brand,
            String category,
            String price,
            int stock,
            String imageUrl) {
        seedProduct(name, description, brand, category, price, stock, List.of(imageUrl));
    }

    private void seedProduct(
            String name,
            String description,
            String brand,
            String category,
            String price,
            int stock,
            List<String> imageUrls) {
        runInTransaction(() -> {
            var existing = productRepository.findByNameIgnoreCase(name);
            if (existing.isPresent()) {
                Product product = existing.get();
                product.setDescription(description);
                product.setBrand(brand);
                product.setCategory(category);
                product.setPrice(new BigDecimal(price));
                product.setStock(stock);
                product.setDeleted(false);
                replaceImages(product, imageUrls);
                productRepository.saveAndFlush(product);
                log.info("Seeded product (update): {} [{} / {}] with {} image(s)",
                        name, brand, category, imageUrls.size());
                return;
            }
            saveProduct(name, description, brand, category, price, stock, imageUrls);
            log.info("Seeded product (create): {} [{} / {}] with {} image(s)",
                    name, brand, category, imageUrls.size());
        });
    }

    private void replaceImages(Product product, List<String> imageUrls) {
        List<ProductImage> images = product.getImages();
        if (images == null) {
            images = new ArrayList<>();
            product.setImages(images);
        } else {
            images.clear();
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage image = new ProductImage(imageUrls.get(i), product.getName());
            image.setSortOrder(i);
            image.setProduct(product);
            images.add(image);
        }
    }

    private void saveProduct(
            String name,
            String description,
            String brand,
            String category,
            String price,
            int stock,
            List<String> imageUrls) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setBrand(brand);
        product.setCategory(category);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);

        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage image = new ProductImage(imageUrls.get(i), name);
            image.setSortOrder(i);
            image.setProduct(product);
            images.add(image);
        }
        product.setImages(images);

        productRepository.saveAndFlush(product);
    }

    private void seedAdminUser() {
        if (adminSeedUsername == null || adminSeedUsername.isBlank()
                || adminSeedPassword == null || adminSeedPassword.isBlank()) {
            return;
        }
        if (userRepository.findByUsername(adminSeedUsername) != null) {
            return;
        }
        Role adminRole = roleRepository.findByName("Admin");
        if (adminRole == null) {
            return;
        }
        User admin = new User();
        admin.setFirstName("Store");
        admin.setLastName("Admin");
        admin.setUsername(adminSeedUsername);
        admin.setEmail(adminSeedUsername + "@pixeltech.local");
        admin.setPassword(PasswordHashingUtility.hashPassword(adminSeedPassword));
        admin.setRole(adminRole);
        userRepository.save(admin);
    }
}
