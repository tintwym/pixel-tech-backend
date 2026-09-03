package com.shopping.cart.config;

import com.shopping.cart.entity.Product;
import com.shopping.cart.entity.ProductImage;
import com.shopping.cart.entity.Role;
import com.shopping.cart.entity.User;
import com.shopping.cart.repository.ProductRepository;
import com.shopping.cart.repository.RoleRepository;
import com.shopping.cart.repository.UserRepository;
import com.shopping.cart.utility.PasswordHashingUtility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {
    private final RoleRepository roleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Value("${app.admin.seed-username:}")
    private String adminSeedUsername;

    @Value("${app.admin.seed-password:}")
    private String adminSeedPassword;

    public DataInitializer(
            RoleRepository roleRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRole("User");
        seedRole("Admin");
        seedSampleProducts();
        seedAdminUser();
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
                "4899000.00", 18,
                "https://images.unsplash.com/photo-1695048133142-1a20484d2569?auto=format&fit=crop&w=600&q=80");
        seedProduct("iPhone 16 — 128GB",
                "A18 chip, Camera Control, longer battery life, and Action Button. Everyday flagship in Ultramarine.",
                "3299000.00", 28,
                "https://images.unsplash.com/photo-1592899677977-9c10ca588bbd?auto=format&fit=crop&w=600&q=80");
        seedProduct("iPhone 17 Pro Max — 256GB",
                "Largest Pro display, advanced camera stack, and all-day battery. Latest Apple flagship for creators and power users.",
                "5899000.00", 12,
                "https://images.unsplash.com/photo-1759820940611-facb87e629d8?auto=format&fit=crop&w=600&q=80");
        seedProduct("Samsung Galaxy S25 Ultra — 256GB",
                "Dynamic AMOLED 2X, S Pen, and pro-grade zoom cameras. Android flagship with DeX and long battery life.",
                "4599000.00", 16,
                "https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?auto=format&fit=crop&w=600&q=80");
        seedProduct("MacBook Air 13\" M3 — 16GB/512GB",
                "Fanless M3 performance, Liquid Retina display, MagSafe, and all-day battery. Ideal for students and light creators.",
                "4299000.00", 14,
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=600&q=80");
        seedProduct("MacBook Pro 14\" M4 Pro — 24GB/512GB",
                "Liquid Retina XDR, pro ports, and sustained performance for code, video, and 3D. Space Black finish.",
                "7899000.00", 8,
                "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=600&q=80");
        seedProduct("ASUS ROG Strix G16",
                "High-refresh gaming laptop with discrete RTX GPU, RGB keyboard, and advanced cooling for esports and creators.",
                "5499000.00", 9,
                "https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=600&q=80");
        seedProduct("AirPods Pro 2 (USB-C)",
                "Active Noise Cancellation, Adaptive Audio, Spatial Audio, and MagSafe charging case with USB-C.",
                "899000.00", 40,
                "https://images.unsplash.com/photo-1600294037681-c80b4cb5b434?auto=format&fit=crop&w=600&q=80");
        seedProduct("Apple Watch Series 10 — 46mm",
                "Thinner aluminum case, brighter Always-On Retina, sleep apnea notifications, and all-day battery.",
                "1599000.00", 22,
                "https://images.unsplash.com/photo-1434493789847-2f02dc6ca35d?auto=format&fit=crop&w=600&q=80");
        seedProduct("iPad Pro 11\" M4 — 256GB",
                "Ultra Retina XDR, Apple Pencil Pro support, and desktop-class M4 performance in a thin tablet.",
                "3899000.00", 11,
                "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=600&q=80");
        seedProduct("Sony WH-1000XM5",
                "Industry-leading noise cancelling headphones with 30-hour battery and multipoint Bluetooth.",
                "1299000.00", 20,
                "https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?auto=format&fit=crop&w=600&q=80");
        seedProduct("Samsung Galaxy Tab S9 — 128GB",
                "AMOLED tablet with included S Pen, DeX mode, and IP68 durability for work and media.",
                "2199000.00", 14,
                "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?auto=format&fit=crop&w=600&q=80");
    }

    /** Soft-delete older fictional demo SKUs so the live catalog stays clean. */
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
                "Portable SSD 1TB"
        );
        for (String name : legacy) {
            productRepository.findByNameIgnoreCase(name).ifPresent(product -> {
                if (!product.isDeleted()) {
                    product.setDeleted(true);
                    productRepository.save(product);
                }
            });
        }
    }

    private void seedProduct(
            String name, String description, String price, int stock, String imageUrl) {
        var existing = productRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            Product product = existing.get();
            product.setDescription(description);
            product.setPrice(new BigDecimal(price));
            product.setStock(stock);
            product.setDeleted(false);
            upsertPrimaryImage(product, imageUrl);
            productRepository.save(product);
            return;
        }
        saveProduct(name, description, price, stock, imageUrl);
    }

    private void upsertPrimaryImage(Product product, String imageUrl) {
        List<ProductImage> images = product.getImages();
        if (images == null || images.isEmpty()) {
            ProductImage image = new ProductImage(imageUrl, product.getName());
            image.setProduct(product);
            List<ProductImage> next = new ArrayList<>();
            next.add(image);
            product.setImages(next);
            return;
        }
        images.get(0).setPath(imageUrl);
        images.get(0).setAltText(product.getName());
    }

    private void saveProduct(String name, String description, String price, int stock, String imagePath) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);

        ProductImage image = new ProductImage(imagePath, name);
        image.setProduct(product);
        List<ProductImage> images = new ArrayList<>();
        images.add(image);
        product.setImages(images);

        productRepository.save(product);
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
