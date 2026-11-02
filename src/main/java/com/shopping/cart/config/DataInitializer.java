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
                || n.contains("imac") || n.contains("mac mini") || n.contains("macmini")
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
        if (n.contains("imac") || n.contains("mac mini") || n.contains("macmini")) {
            return "Desktops / PCs";
        }
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
                "6.9\" Super Retina XDR, next-gen Pro camera system, and all-day battery. Finishes: Black, Silver, Glacier, and Burgundy.",
                "Apple", "Smartphones", "6499000.00", 10,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247381/pixel-tech/products/iphone-18-pro-model-unselect-gallery-1-202609-22a5861e.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247383/pixel-tech/products/iphone-18-pro-model-unselect-gallery-2-202609-92dadce4.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247358/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-black-975868e5.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247359/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-black-av1-287a570e.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247360/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-black-av2-ed432c8d.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247361/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-burgundy-615d5bf5.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247362/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-burgundy-av1-2f696fb7.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247363/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-burgundy-av2-c79a9815.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247364/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-glacier-3f3bbb92.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247364/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-glacier-av1-2cf90f3d.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247366/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-glacier-av2-59af19a0.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247366/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-silver-fefb474d.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247367/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-silver-av1-c8703f2a.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247368/pixel-tech/products/iphone-18-pro-finish-select-202609-6-9inch-silver-av2-ab480a55.webp"
                ));
        seedProduct("iPhone 18 Pro — 256GB",
                "6.3\" Super Retina XDR, Pro camera system, and titanium design. Finishes: Black, Silver, Glacier, and Burgundy.",
                "Apple", "Smartphones", "5899000.00", 14,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247381/pixel-tech/products/iphone-18-pro-model-unselect-gallery-1-202609-22a5861e.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247383/pixel-tech/products/iphone-18-pro-model-unselect-gallery-2-202609-92dadce4.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247370/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-black-fa73017a.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247371/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-black-av1-31f88d36.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247371/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-black-av2-b17481d6.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247372/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-burgundy-15f48512.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247373/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-burgundy-av1-6604a66b.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247374/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-burgundy-av2-64d0863c.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247375/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-glacier-91564f14.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247375/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-glacier-av1-eba78402.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247376/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-glacier-av2-12c15eee.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247377/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-silver-53f9cb94.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247378/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-silver-av1-92c90f1d.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247379/pixel-tech/products/iphone-18-pro-finish-select-202609-6-3inch-silver-av2-57dd9ef2.webp"
                ));
        seedProduct("iPhone Air — 256GB",
                "Ultra-thin design with brilliant Super Retina display and all-day battery. Finishes: Cloud White, Sky Blue, Space Black, and Light Gold.",
                "Apple", "Smartphones", "4299000.00", 18,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247394/pixel-tech/products/iphone-air-finish-unselect-gallery-1-202509-75b52dcb.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247395/pixel-tech/products/iphone-air-finish-unselect-gallery-2-202509-41efefc4.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247395/pixel-tech/products/iphone-air-finish-unselect-gallery-3-202509-013ee935.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247396/pixel-tech/products/iphone-air-finish-unselect-gallery-4-202509-d326b7df.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247397/pixel-tech/products/iphone-air-finish-unselect-gallery-5-202509-d3ae8cb9.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247384/pixel-tech/products/iphone-air-finish-select-202509-cloudwhite-2f8aabee.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247384/pixel-tech/products/iphone-air-finish-select-202509-cloudwhite-av1-6fc4ff05.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247385/pixel-tech/products/iphone-air-finish-select-202509-cloudwhite-av2-64a1e44f.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247386/pixel-tech/products/iphone-air-finish-select-202509-lightgold-40e2299e.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247387/pixel-tech/products/iphone-air-finish-select-202509-lightgold-av1-c8755990.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247388/pixel-tech/products/iphone-air-finish-select-202509-lightgold-av2-bd70a3b9.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247389/pixel-tech/products/iphone-air-finish-select-202509-skyblue-86ddc338.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247389/pixel-tech/products/iphone-air-finish-select-202509-skyblue-av1-0652f01b.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247390/pixel-tech/products/iphone-air-finish-select-202509-skyblue-av2-6d92d5d5.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247392/pixel-tech/products/iphone-air-finish-select-202509-spaceblack-84f94c02.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247393/pixel-tech/products/iphone-air-finish-select-202509-spaceblack-av1-631c7d3e.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247393/pixel-tech/products/iphone-air-finish-select-202509-spaceblack-av2-e433b15d.webp"
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
        seedProduct("iPhone 17 — 256GB",
                "A19 performance, Center Stage front camera, dual Fusion rear cameras, and all-day battery. Finishes: Black, White, Mist Blue, Sage, and Lavender.",
                "Apple", "Smartphones", "3899000.00", 22,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247047/pixel-tech/products/iphone-17-finish-unselect-gallery-1-202509-14770efc-b2a5-49b1-beb8-f5372a10e49e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247048/pixel-tech/products/iphone-17-finish-unselect-gallery-2-202509-efd4a88e-6e6a-4d2c-af2e-01bcd76b4bd3.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247049/pixel-tech/products/iphone-17-finish-unselect-gallery-3-202509-b953e53e-8e11-4bb7-b933-5e5bec75d79f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247050/pixel-tech/products/iphone-17-finish-unselect-gallery-4-202509-f3a35011-6beb-4e2e-8c0c-b44a676ff493.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247031/pixel-tech/products/iphone-17-finish-select-202509-black-b5180f78-8fe8-446a-beb0-da5e4699ee85.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247032/pixel-tech/products/iphone-17-finish-select-202509-black_AV1-967f6b29-4449-4757-95c5-9170f936386b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247033/pixel-tech/products/iphone-17-finish-select-202509-black_AV2-6b74ad8f-6e63-440b-828a-36da5325e4eb.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247044/pixel-tech/products/iphone-17-finish-select-202509-white-b02e99f5-3515-4a9f-b567-fdede9ad713b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247045/pixel-tech/products/iphone-17-finish-select-202509-white_AV1-ef428d76-a2e2-4168-a3c2-9a4cc044b512.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247046/pixel-tech/products/iphone-17-finish-select-202509-white_AV2-7c422c3d-83ab-432a-9d72-28b16dd5c986.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247037/pixel-tech/products/iphone-17-finish-select-202509-mistblue-bc8b91b9-8558-480e-b020-0c19c8d319b1.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247038/pixel-tech/products/iphone-17-finish-select-202509-mistblue_AV1-e11b083d-d03f-433f-b921-b0308913d81b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247039/pixel-tech/products/iphone-17-finish-select-202509-mistblue_AV2-cdff0a57-ae9b-465a-a2a9-fb1a9c447b32.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247040/pixel-tech/products/iphone-17-finish-select-202509-sage-25177978-c80e-427f-bffc-b63e2cfc7e2e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247042/pixel-tech/products/iphone-17-finish-select-202509-sage_AV1-cddf9f29-c2a5-40ab-8163-1e28af4d3f7b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247043/pixel-tech/products/iphone-17-finish-select-202509-sage_AV2-c6b79801-cd80-4328-b14e-48577e6c3c0f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247034/pixel-tech/products/iphone-17-finish-select-202509-lavender-8a127f1b-066f-46ed-b3a4-4e83f51f7a99.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247035/pixel-tech/products/iphone-17-finish-select-202509-lavender_AV1-938c7747-eb19-4c82-96e7-cd02f7866fb5.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793247036/pixel-tech/products/iphone-17-finish-select-202509-lavender_AV2-696b8b6d-56de-4a10-b277-18e0fddfb0cf.jpg"
                ));
        seedProduct("Samsung Galaxy S25 Ultra — 256GB",
                "Dynamic AMOLED 2X, S Pen, and pro-grade zoom cameras. Android flagship with DeX and long battery life.",
                "Samsung", "Smartphones", "4599000.00", 16,
                "https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?auto=format&fit=crop&w=600&q=80");
        seedProduct("MacBook Air",
                "M5 Liquid Retina laptop with silent fanless design and all-day battery. Choose 13\" or 15\", memory, and storage. Finishes: Midnight, Starlight, Silver, and Sky Blue.",
                "Apple", "Laptops", "4299000.00", 14,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248210/pixel-tech/products/macbook-air-size-unselect-202601-gallery-1-710da233.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248210/pixel-tech/products/macbook-air-size-unselect-202601-gallery-2-fc033efd.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793258999/pixel-tech/products/macbook-air-size-select-202601-13inch-ac5c0283.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259000/pixel-tech/products/macbook-air-size-select-202601-13inch-av1-9705938d.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259002/pixel-tech/products/macbook-air-size-select-202601-13inch-av2-186f7b0f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259006/pixel-tech/products/macbook-air-size-select-202601-15inch-ccf36fc4.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259003/pixel-tech/products/macbook-air-size-select-202601-15inch-av1-aab2ce7e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259005/pixel-tech/products/macbook-air-size-select-202601-15inch-av2-cdf02ce3.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248192/pixel-tech/products/macbook-air-finish-select-202601-13inch-midnight-25be94ea.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248193/pixel-tech/products/macbook-air-finish-select-202601-13inch-midnight-av1-1d88c977.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248194/pixel-tech/products/macbook-air-finish-select-202601-13inch-midnight-av2-f183b2b0.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248195/pixel-tech/products/macbook-air-finish-select-202601-13inch-midnight-av3-31e11bee.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248196/pixel-tech/products/macbook-air-finish-select-202601-13inch-silver-c061be3c.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248197/pixel-tech/products/macbook-air-finish-select-202601-13inch-silver-av1-850a61e0.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248198/pixel-tech/products/macbook-air-finish-select-202601-13inch-silver-av2-42d2391f.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248199/pixel-tech/products/macbook-air-finish-select-202601-13inch-silver-av3-d53b9f4d.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248199/pixel-tech/products/macbook-air-finish-select-202601-13inch-skyblue-af10ba5a.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248200/pixel-tech/products/macbook-air-finish-select-202601-13inch-skyblue-av1-c05ef7bf.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248201/pixel-tech/products/macbook-air-finish-select-202601-13inch-skyblue-av2-e1a07e13.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248202/pixel-tech/products/macbook-air-finish-select-202601-13inch-skyblue-av3-79e909f3.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248203/pixel-tech/products/macbook-air-finish-select-202601-13inch-starlight-157b15cc.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248203/pixel-tech/products/macbook-air-finish-select-202601-13inch-starlight-av1-3960452d.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248204/pixel-tech/products/macbook-air-finish-select-202601-13inch-starlight-av2-a195cdf1.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248205/pixel-tech/products/macbook-air-finish-select-202601-13inch-starlight-av3-ad820074.webp"
                ));
        seedProduct("MacBook Pro PROLED",
                "Most advanced MacBook Pro with PROLED XDR (Tandem OLED) touch display, M5 Pro or M5 Max, and up to 24-hour battery. Choose 14.2\" or 16.3\", memory, and storage. Finishes: Space Black and Silver.",
                "Apple", "Laptops", "8999000.00", 10,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259216/pixel-tech/products/macbook-pro-proled-size-unselect-202610-gallery-1-4d56699f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259218/pixel-tech/products/macbook-pro-proled-size-unselect-202610-gallery-2-4195d647.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259210/pixel-tech/products/macbook-pro-proled-size-select-202610-14inch-e75ea356.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259207/pixel-tech/products/macbook-pro-proled-size-select-202610-14inch-av1-82c363ee.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259209/pixel-tech/products/macbook-pro-proled-size-select-202610-14inch-av2-aab5e05a.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259212/pixel-tech/products/macbook-pro-proled-size-select-202610-16inch-159979c9.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259213/pixel-tech/products/macbook-pro-proled-size-select-202610-16inch-av1-b59d54de.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259215/pixel-tech/products/macbook-pro-proled-size-select-202610-16inch-av2-b6df6d3d.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259196/pixel-tech/products/macbook-pro-proled-finish-select-202610-14inch-spaceblack-48f19015.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259199/pixel-tech/products/macbook-pro-proled-finish-select-202610-14inch-spaceblack-av2-a066be06.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259201/pixel-tech/products/macbook-pro-proled-finish-select-202610-14inch-spaceblack-av4-7b1f333b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259204/pixel-tech/products/macbook-pro-proled-finish-select-202610-14inch-spaceblack-av6-6e4948fd.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259189/pixel-tech/products/macbook-pro-proled-finish-select-202610-14inch-silver-85241a17.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259190/pixel-tech/products/macbook-pro-proled-finish-select-202610-14inch-silver-av1-b2bb4b3f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259192/pixel-tech/products/macbook-pro-proled-finish-select-202610-14inch-silver-av2-8b656892.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793259194/pixel-tech/products/macbook-pro-proled-finish-select-202610-14inch-silver-av3-774ecce7.jpg"
                ));
        seedProduct("iMac",
                "24\" 4.5K Retina all-in-one with M6, MagSafe, Center Stage camera, Wi-Fi 7, and matching Magic Keyboard + Magic Mouse. Colors: Blue, Purple, Pink, Orange, Yellow, Green, and Silver.",
                "Apple", "Desktops / PCs", "5499000.00", 12,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260817/pixel-tech/products/imac-color-unselect-202610-gallery-1-b7851aa6.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260818/pixel-tech/products/imac-color-unselect-202610-gallery-2-d5fe8ae7.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260820/pixel-tech/products/imac-color-unselect-202610-gallery-3-e063398c.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260821/pixel-tech/products/imac-color-unselect-202610-gallery-4-31e8eb56.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260823/pixel-tech/products/imac-color-unselect-202610-gallery-5-ad42f01d.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260805/pixel-tech/products/imac-color-select-202610-blue-585bb601.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260812/pixel-tech/products/imac-color-select-202610-purple-97972890.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260810/pixel-tech/products/imac-color-select-202610-pink-429873cb.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260809/pixel-tech/products/imac-color-select-202610-orange-3ab41c5e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260815/pixel-tech/products/imac-color-select-202610-yellow-fe9c0acf.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260807/pixel-tech/products/imac-color-select-202610-green-8a400acb.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260813/pixel-tech/products/imac-color-select-202610-silver-7ca1a1fd.jpg"
                ));
        seedProduct("Mac mini",
                "Ultracompact 5×5\" desktop with M6 or M5 Pro, front USB-C + headphone jack, Thunderbolt, HDMI, 2.5Gb Ethernet, Wi-Fi 7, and Bluetooth 6. Finish: Silver.",
                "Apple", "Desktops / PCs", "2899000.00", 18,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260962/pixel-tech/products/mac-mini-chip-unselect-202608-gallery-1-4daec40b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260963/pixel-tech/products/mac-mini-chip-unselect-202608-gallery-2-ca8dce7b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260965/pixel-tech/products/mac-mini-chip-unselect-202608-gallery-3-3231b5ad.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793260967/pixel-tech/products/mac-mini-chip-unselect-202608-gallery-4-5a3fbf2d.jpg"
                ));
        seedProduct("MacBook Neo",
                "Colorful everyday MacBook with vivid finishes (Blush, Citrus, Indigo, Silver). Configure memory and storage to match your workflow.",
                "Apple", "Laptops", "3599000.00", 16,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248225/pixel-tech/products/macbook-neo-color-unselect-202603-gallery-1-c2291527.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248211/pixel-tech/products/macbook-neo-color-select-202603-blush-c9b05537.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248212/pixel-tech/products/macbook-neo-color-select-202603-blush-av1-8aac8c27.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248213/pixel-tech/products/macbook-neo-color-select-202603-blush-av2-f85418ef.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248214/pixel-tech/products/macbook-neo-color-select-202603-blush-av3-5ec750ee.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248215/pixel-tech/products/macbook-neo-color-select-202603-citrus-b1b63c70.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248216/pixel-tech/products/macbook-neo-color-select-202603-citrus-av1-03a22986.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248216/pixel-tech/products/macbook-neo-color-select-202603-citrus-av2-9d837bdf.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248217/pixel-tech/products/macbook-neo-color-select-202603-citrus-av3-a22fc654.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248218/pixel-tech/products/macbook-neo-color-select-202603-indigo-e9438a5e.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248219/pixel-tech/products/macbook-neo-color-select-202603-indigo-av1-3b11268d.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248219/pixel-tech/products/macbook-neo-color-select-202603-indigo-av2-251bf219.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248221/pixel-tech/products/macbook-neo-color-select-202603-indigo-av3-01c914e4.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248222/pixel-tech/products/macbook-neo-color-select-202603-silver-93ac1172.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248223/pixel-tech/products/macbook-neo-color-select-202603-silver-av1-3bf7d2ee.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248224/pixel-tech/products/macbook-neo-color-select-202603-silver-av2-6143999c.webp",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793248224/pixel-tech/products/macbook-neo-color-select-202603-silver-av3-e4b49fa4.webp"
                ));
        seedProduct("MacBook Pro",
                "Liquid Retina XDR with M6, M5 Pro, or M5 Max for code, video, and 3D. Choose 14.2\" or 16.2\", memory, and storage. Finishes: Space Black and Silver.",
                "Apple", "Laptops", "7899000.00", 8,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587620/pixel-tech/products/macbook-pro-size-unselect-202610-gallery-1-86b32bdd.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587622/pixel-tech/products/macbook-pro-size-unselect-202610-gallery-2-d87ac477.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587611/pixel-tech/products/macbook-pro-size-select-202610-14inch-2517c0be.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587613/pixel-tech/products/macbook-pro-size-select-202610-14inch-av1-95488922.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587614/pixel-tech/products/macbook-pro-size-select-202610-14inch-av2-46e83320.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587616/pixel-tech/products/macbook-pro-size-select-202610-16inch-59b84d4a.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587617/pixel-tech/products/macbook-pro-size-select-202610-16inch-av1-260f8ea8.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587619/pixel-tech/products/macbook-pro-size-select-202610-16inch-av2-955a4dd9.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587595/pixel-tech/products/macbook-pro-finish-select-202610-14inch-spaceblack-509d5e46.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587596/pixel-tech/products/macbook-pro-finish-select-202610-14inch-spaceblack-83d62221.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587597/pixel-tech/products/macbook-pro-finish-select-202610-14inch-spaceblack-ca65ad79.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587598/pixel-tech/products/macbook-pro-finish-select-202610-14inch-spaceblack-d4bc0e01.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587589/pixel-tech/products/macbook-pro-finish-select-202610-14inch-silver-0316b894.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587590/pixel-tech/products/macbook-pro-finish-select-202610-14inch-silver-baf7bf43.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587592/pixel-tech/products/macbook-pro-finish-select-202610-14inch-silver-d8fed08b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587593/pixel-tech/products/macbook-pro-finish-select-202610-14inch-silver-f2e4d17b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587605/pixel-tech/products/macbook-pro-finish-select-202610-16inch-spaceblack-13138a2e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587607/pixel-tech/products/macbook-pro-finish-select-202610-16inch-spaceblack-2dcc5959.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587608/pixel-tech/products/macbook-pro-finish-select-202610-16inch-spaceblack-3a11e902.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587610/pixel-tech/products/macbook-pro-finish-select-202610-16inch-spaceblack-93f5f759.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587600/pixel-tech/products/macbook-pro-finish-select-202610-16inch-silver-6129d0a1.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587601/pixel-tech/products/macbook-pro-finish-select-202610-16inch-silver-7331f875.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587603/pixel-tech/products/macbook-pro-finish-select-202610-16inch-silver-8a9bff0d.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793587604/pixel-tech/products/macbook-pro-finish-select-202610-16inch-silver-8abd3f8e.jpg"
                ));
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
        seedProduct("iPad Pro",
                "Ultra Retina XDR (tandem OLED) with M5, Apple Pencil Pro support, and an ultrasleek design. Choose 11\" or 13\", storage, and finish. Finishes: Silver and Space Black.",
                "Apple", "Tablets", "3899000.00", 14,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586887/pixel-tech/products/ipad-pro-finish-unselect-202405-gallery-1-887e9897.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586889/pixel-tech/products/ipad-pro-finish-unselect-202405-gallery-2-0aca927c.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586890/pixel-tech/products/ipad-pro-finish-unselect-202405-gallery-3-0feca3b8.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586892/pixel-tech/products/ipad-pro-size-select-202405-gallery-1-b13e916e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586894/pixel-tech/products/ipad-pro-size-select-202405-gallery-2-f83504a4.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586874/pixel-tech/products/ipad-pro-finish-select-202405-11inch-spaceblack-0fba0363.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586875/pixel-tech/products/ipad-pro-finish-select-202405-11inch-spaceblack-av1-dde85ee7.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586869/pixel-tech/products/ipad-pro-finish-select-202405-11inch-silver-0d103e4b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586871/pixel-tech/products/ipad-pro-finish-select-202405-11inch-silver-av1-3c3bb3e2.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586872/pixel-tech/products/ipad-pro-finish-select-202405-11inch-silver-av2-a39aa37b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586882/pixel-tech/products/ipad-pro-finish-select-202405-13inch-spaceblack-2f779cf9.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586883/pixel-tech/products/ipad-pro-finish-select-202405-13inch-spaceblack-av1-7b6315e6.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586884/pixel-tech/products/ipad-pro-finish-select-202405-13inch-spaceblack-av2-785ab443.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586886/pixel-tech/products/ipad-pro-finish-select-202405-13inch-spaceblack-av3-5a9a70e9.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586877/pixel-tech/products/ipad-pro-finish-select-202405-13inch-silver-aed04d43.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586878/pixel-tech/products/ipad-pro-finish-select-202405-13inch-silver-av1-632aff5e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793586880/pixel-tech/products/ipad-pro-finish-select-202405-13inch-silver-av2-4c54d26b.jpg"
                ));
        seedProduct("iPad Air",
                "Liquid Retina with M2, Landscape 12MP Ultra Wide front camera, Apple Pencil Pro support, and an all-day battery. Choose 11\" or 13\", storage, and finish. Finishes: Blue, Purple, Starlight, and Space Gray.",
                "Apple", "Tablets", "2499000.00", 16,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588037/pixel-tech/products/ipad-air-finish-unselect-202405-gallery-1-a0930d7e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588038/pixel-tech/products/ipad-air-finish-unselect-202405-gallery-2-71ccaba7.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588040/pixel-tech/products/ipad-air-size-select-202405-gallery-1-535d6571.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588041/pixel-tech/products/ipad-air-size-select-202405-gallery-2-78db153a.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588043/pixel-tech/products/ipad-air-size-select-202405-gallery-3-bc976ee1.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588044/pixel-tech/products/ipad-air-size-select-202405-gallery-4-49ba6b29.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588046/pixel-tech/products/ipad-air-finish-select-202405-11inch-blue-4a01b240.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588047/pixel-tech/products/ipad-air-finish-select-202405-11inch-blue-av1-df622129.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588048/pixel-tech/products/ipad-air-finish-select-202405-11inch-blue-av2-de7b813f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588050/pixel-tech/products/ipad-air-finish-select-202405-11inch-purple-085421bd.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588051/pixel-tech/products/ipad-air-finish-select-202405-11inch-purple-av1-e9e396c6.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588052/pixel-tech/products/ipad-air-finish-select-202405-11inch-purple-av2-da1bcbd9.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588054/pixel-tech/products/ipad-air-finish-select-202405-11inch-spacegray-fb01e709.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588055/pixel-tech/products/ipad-air-finish-select-202405-11inch-spacegray-av1-63784399.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588057/pixel-tech/products/ipad-air-finish-select-202405-11inch-spacegray-av2-13854f3d.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588058/pixel-tech/products/ipad-air-finish-select-202405-11inch-starlight-896ab99d.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588059/pixel-tech/products/ipad-air-finish-select-202405-11inch-starlight-av1-1bf58da1.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588061/pixel-tech/products/ipad-air-finish-select-202405-11inch-starlight-av2-ac95c676.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588062/pixel-tech/products/ipad-air-finish-select-202405-13inch-blue-fe93bd5b.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588064/pixel-tech/products/ipad-air-finish-select-202405-13inch-blue-av1-aafdca66.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588065/pixel-tech/products/ipad-air-finish-select-202405-13inch-blue-av2-23b20404.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588066/pixel-tech/products/ipad-air-finish-select-202405-13inch-purple-8c037c32.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588068/pixel-tech/products/ipad-air-finish-select-202405-13inch-purple-av1-2221ecd7.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588070/pixel-tech/products/ipad-air-finish-select-202405-13inch-purple-av2-b191ef39.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588071/pixel-tech/products/ipad-air-finish-select-202405-13inch-spacegray-15c8862c.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588073/pixel-tech/products/ipad-air-finish-select-202405-13inch-spacegray-av1-f0400c43.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588074/pixel-tech/products/ipad-air-finish-select-202405-13inch-spacegray-av2-67650137.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588075/pixel-tech/products/ipad-air-finish-select-202405-13inch-starlight-4d0e10df.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588076/pixel-tech/products/ipad-air-finish-select-202405-13inch-starlight-av1-70244de9.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588078/pixel-tech/products/ipad-air-finish-select-202405-13inch-starlight-av2-f1ce8da3.jpg"
                ));
        seedProduct("iPad",
                "11th-generation iPad with the A16 chip, 11\" Liquid Retina display, Landscape 12MP Ultra Wide front camera, and USB-C. Choose storage and finish. Finishes: Blue, Pink, Yellow, and Silver.",
                "Apple", "Tablets", "1499000.00", 20,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588214/pixel-tech/products/ipad-finish-unselect-202503-gallery-1-7f64bfc3.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588216/pixel-tech/products/ipad-finish-unselect-202503-gallery-2-c90e562e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588217/pixel-tech/products/ipad-finish-unselect-202503-gallery-3-0159b04a.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588219/pixel-tech/products/ipad-finish-select-202503-blue-68f016f4.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588220/pixel-tech/products/ipad-finish-select-202503-blue-av1-f2fe0ad4.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588221/pixel-tech/products/ipad-finish-select-202503-blue-av2-f65f577f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588223/pixel-tech/products/ipad-finish-select-202503-pink-7a74fe8e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588224/pixel-tech/products/ipad-finish-select-202503-pink-av1-6980fef3.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588226/pixel-tech/products/ipad-finish-select-202503-pink-av2-644cdb07.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588227/pixel-tech/products/ipad-finish-select-202503-silver-8210c1b8.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588228/pixel-tech/products/ipad-finish-select-202503-silver-av1-b5cb2ffa.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588230/pixel-tech/products/ipad-finish-select-202503-silver-av2-c88050e2.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588231/pixel-tech/products/ipad-finish-select-202503-yellow-6c62ce6f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588232/pixel-tech/products/ipad-finish-select-202503-yellow-av1-2a86b0cd.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588234/pixel-tech/products/ipad-finish-select-202503-yellow-av2-f80e0d9e.jpg"
                ));
        seedProduct("iPad mini",
                "Compact 8.3\" Liquid Retina with all-day battery, Landscape 12MP Ultra Wide front camera, and Apple Pencil support. Choose storage and finish. Finishes: Midnight and Starlight.",
                "Apple", "Tablets", "1899000.00", 18,
                List.of(
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588695/pixel-tech/products/ipad-mini-finish-unselect-202610-gallery-1-e883b88e.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588697/pixel-tech/products/ipad-mini-finish-unselect-202610-gallery-2-b875a554.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588698/pixel-tech/products/ipad-mini-finish-unselect-202610-gallery-3-809d0c50.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588700/pixel-tech/products/ipad-mini-finish-select-202610-midnight-f3fb254f.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588702/pixel-tech/products/ipad-mini-finish-select-202610-midnight-av1-5b41c0da.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588703/pixel-tech/products/ipad-mini-finish-select-202610-midnight-av2-c4aefee4.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588705/pixel-tech/products/ipad-mini-finish-select-202610-starlight-7b42e3c8.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588706/pixel-tech/products/ipad-mini-finish-select-202610-starlight-av1-e700ffcc.jpg",
                        "https://res.cloudinary.com/diqkh6yqr/image/upload/v1793588708/pixel-tech/products/ipad-mini-finish-select-202610-starlight-av2-ae8d8a0f.jpg"
                ));
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
                "iPad Pro 11\" M4 — 256GB",
                "Crystal 4K Webcam",
                "Aero Mechanical Keyboard",
                "Summit Phone Lite",
                "Studio Monitor 27\" 2K",
                "Portable SSD 1TB",
                "MacBook Air 13\" M3 — 16GB/512GB",
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
        try {
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
                    boolean imagesChanged = replaceImagesIfNeeded(product, imageUrls);
                    productRepository.save(product);
                    log.info("Seeded product (update{}): {} [{} / {}] with {} image(s)",
                            imagesChanged ? "+images" : "", name, brand, category, imageUrls.size());
                    return;
                }
                saveProduct(name, description, brand, category, price, stock, imageUrls);
                log.info("Seeded product (create): {} [{} / {}] with {} image(s)",
                        name, brand, category, imageUrls.size());
            });
        } catch (Exception e) {
            log.error("Failed to seed product {}: {}", name, e.toString());
        }
    }

    /** Returns true when gallery rows were rewritten (skip no-op deletes on Neon). */
    private boolean replaceImagesIfNeeded(Product product, List<String> imageUrls) {
        List<ProductImage> images = product.getImages();
        if (images != null && images.size() == imageUrls.size()) {
            boolean same = true;
            for (int i = 0; i < imageUrls.size(); i++) {
                ProductImage current = images.get(i);
                String path = current == null ? null : current.getPath();
                if (path == null || !path.equals(imageUrls.get(i))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return false;
            }
        }
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
        return true;
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
