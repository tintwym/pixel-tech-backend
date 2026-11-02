package com.shopping.cart.service;

import com.shopping.cart.entity.Cart;
import com.shopping.cart.entity.CartItem;
import com.shopping.cart.entity.Product;
import com.shopping.cart.entity.User;
import com.shopping.cart.interfaces.ICartService;
import com.shopping.cart.repository.CartRepository;
import com.shopping.cart.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartService implements ICartService {
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final UserService userService;

    public CartService(ProductRepository productRepository, CartRepository cartRepository, UserService userService) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.userService = userService;
    }

    @Override
    @Transactional(readOnly = true)
    public int getCartItemCount(String token) {
        User user = userService.requireUser(token);
        Cart cart = cartRepository.findByUserWithItems(user);

        if (cart == null || cart.getCartItems().isEmpty()) {
            return 0;
        }

        return cart.getCartItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    @Override
    @Transactional(readOnly = true)
    public Cart getCartByUser(String token) {
        User user = userService.requireUser(token);
        Cart cart = cartRepository.findByUserWithItems(user);
        if (cart == null) {
            Cart empty = new Cart(BigDecimal.ZERO, user);
            empty.setCartItems(new ArrayList<>());
            return empty;
        }
        initializeProductImages(cart);
        return cart;
    }

    @Override
    @Transactional
    public void addProductToCart(String token, UUID productId, int quantity, String variantLabel) {
        User user = userService.requireUser(token);

        Cart cart = cartRepository.findByUserWithItems(user);
        if (cart == null) {
            cart = new Cart(BigDecimal.ZERO, user);
            cart = cartRepository.save(cart);
        }

        Product product = productRepository.findById(Objects.requireNonNull(productId))
                .orElseThrow(() -> new IllegalStateException("Product not found"));

        if (product.isDeleted()) {
            throw new IllegalStateException("Product is no longer available");
        }
        if (quantity <= 0) {
            throw new IllegalStateException("Quantity must be greater than 0");
        }

        String normalizedVariant = normalizeVariant(variantLabel);
        int otherQty = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId)
                        && !matchesVariant(item, normalizedVariant))
                .mapToInt(CartItem::getQuantity)
                .sum();

        Optional<CartItem> existingCartItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId)
                        && matchesVariant(item, normalizedVariant))
                .findFirst();

        int currentLineQty = existingCartItem.map(CartItem::getQuantity).orElse(0);
        int newLineQty = currentLineQty + quantity;
        if (product.getStock() < otherQty + newLineQty) {
            throw new IllegalStateException("Insufficient stock");
        }

        if (existingCartItem.isPresent()) {
            CartItem cartItem = existingCartItem.get();
            cartItem.setQuantity(newLineQty);
            cartItem.setPrice(product.getPrice().multiply(BigDecimal.valueOf(newLineQty)));
            if (cartItem.getVariantLabel() == null && !normalizedVariant.isEmpty()) {
                cartItem.setVariantLabel(normalizedVariant);
            }
        } else {
            CartItem cartItem = new CartItem(
                    cart, product, quantity, product.getPrice().multiply(BigDecimal.valueOf(quantity)));
            cartItem.setVariantLabel(normalizedVariant.isEmpty() ? null : normalizedVariant);
            cart.getCartItems().add(cartItem);
        }

        cart.setTotalPrice(sumCart(cart));
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void updateProductInCart(String token, UUID productId, int quantity, String variantLabel) {
        User user = userService.requireUser(token);

        Cart cart = cartRepository.findByUserWithItems(user);
        if (cart == null) {
            throw new IllegalStateException("Cart not found");
        }

        Product product = productRepository.findById(Objects.requireNonNull(productId))
                .orElseThrow(() -> new IllegalStateException("Product not found"));

        if (product.isDeleted()) {
            throw new IllegalStateException("Product is no longer available");
        }

        String normalizedVariant = normalizeVariant(variantLabel);
        CartItem cartItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId)
                        && matchesVariant(item, normalizedVariant))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Product is not in the cart"));

        if (quantity <= 0) {
            throw new IllegalStateException("Quantity must be greater than 0");
        }

        int otherQty = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId)
                        && !matchesVariant(item, normalizedVariant))
                .mapToInt(CartItem::getQuantity)
                .sum();
        if (product.getStock() < otherQty + quantity) {
            throw new IllegalStateException("Insufficient stock");
        }

        cartItem.setQuantity(quantity);
        cartItem.setPrice(product.getPrice().multiply(BigDecimal.valueOf(quantity)));

        cart.setTotalPrice(sumCart(cart));
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void deleteProductFromCart(String token, UUID productId, String variantLabel) {
        User user = userService.requireUser(token);

        Cart cart = cartRepository.findByUserWithItems(user);
        if (cart == null) {
            throw new IllegalStateException("Cart not found");
        }

        if (!productRepository.existsById(Objects.requireNonNull(productId))) {
            throw new IllegalStateException("Product not found");
        }

        String normalizedVariant = normalizeVariant(variantLabel);
        CartItem cartItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId)
                        && matchesVariant(item, normalizedVariant))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Product is not in the cart"));

        cart.getCartItems().remove(cartItem);
        cart.setTotalPrice(sumCart(cart));
        cartRepository.save(cart);
    }

    private static BigDecimal sumCart(Cart cart) {
        return cart.getCartItems().stream()
                .map(item -> Objects.requireNonNullElse(item.getPrice(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String normalizeVariant(String variantLabel) {
        return variantLabel == null ? "" : variantLabel.trim();
    }

    private static boolean matchesVariant(CartItem item, String normalizedVariant) {
        return normalizeVariant(item.getVariantLabel()).equals(normalizedVariant);
    }

    /** Force-load images while the transaction is open (OSIV is disabled). */
    private void initializeProductImages(Cart cart) {
        if (cart == null || cart.getCartItems() == null) {
            return;
        }
        for (CartItem item : cart.getCartItems()) {
            if (item.getProduct() != null && item.getProduct().getImages() != null) {
                item.getProduct().getImages().size();
            }
        }
    }
}
