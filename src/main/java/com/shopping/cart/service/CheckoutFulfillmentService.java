package com.shopping.cart.service;

import com.shopping.cart.entity.*;
import com.shopping.cart.repository.*;
import com.shopping.cart.utility.StripeMoney;
import com.stripe.exception.StripeException;
import com.stripe.model.LineItem;
import com.stripe.model.LineItemCollection;
import com.stripe.model.Price;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionListLineItemsParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a paid Stripe Checkout Session into an order (idempotent), adjusts stock,
 * records payment, and clears paid cart lines. Used by the Stripe webhook and by the
 * post-checkout sync endpoint.
 */
@Service
public class CheckoutFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutFulfillmentService.class);

    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    public CheckoutFulfillmentService(
            PlatformTransactionManager transactionManager,
            EntityManager entityManager,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductRepository productRepository,
            CartRepository cartRepository,
            UserRepository userRepository) {
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.entityManager = entityManager;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
    }

    public Session retrievePaidSession(String sessionId) throws StripeException {
        SessionRetrieveParams params = SessionRetrieveParams.builder().build();
        Session session = Session.retrieve(sessionId, params, null);
        if (!"paid".equals(session.getPaymentStatus())) {
            throw new IllegalStateException("Checkout session is not paid yet: " + session.getPaymentStatus());
        }
        return session;
    }

    /** Loads all checkout line items across Stripe pagination pages. */
    public List<LineItem> listAllLineItems(Session session) throws StripeException {
        SessionListLineItemsParams params = SessionListLineItemsParams.builder()
                .setLimit(100L)
                .addExpand("data.price.product")
                .build();
        LineItemCollection collection = session.listLineItems(params);
        List<LineItem> all = new ArrayList<>();
        for (LineItem lineItem : collection.autoPagingIterable()) {
            all.add(lineItem);
        }
        return all;
    }

    /**
     * Idempotent fulfillment for a Checkout Session. Stripe I/O happens outside the DB transaction.
     * DB uniqueness on stripe_checkout_session_id provides cross-instance idempotency.
     */
    public void fulfillBySessionId(String sessionId) throws StripeException {
        Session session = retrievePaidSession(sessionId);
        List<LineItem> lineItems = listAllLineItems(session);
        fulfillAfterRetrieve(sessionId, session, lineItems);
    }

    /**
     * Verifies the paid session belongs to {@code user}, then fulfills idempotently.
     */
    public void confirmSessionForUser(String sessionId, User user) throws StripeException {
        Session session = retrievePaidSession(sessionId);
        Map<String, String> metadata = session.getMetadata();
        String ownerId = metadata != null ? metadata.get("user_id") : null;
        if (ownerId == null || !user.getId().toString().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This checkout session does not belong to the current user");
        }
        List<LineItem> lineItems = listAllLineItems(session);
        fulfillAfterRetrieve(sessionId, session, lineItems);
    }

    public void fulfillAfterRetrieve(String sessionId, Session session) throws StripeException {
        fulfillAfterRetrieve(sessionId, session, listAllLineItems(session));
    }

    public void fulfillAfterRetrieve(String sessionId, Session session, List<LineItem> lineItems) {
        if (!"paid".equals(session.getPaymentStatus())) {
            throw new IllegalStateException("Checkout session is not paid yet: " + session.getPaymentStatus());
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (orderRepository.findByStripeCheckoutSessionId(sessionId).isPresent()) {
                return;
            }
            if (session.getMetadata() == null || session.getMetadata().get("user_id") == null) {
                throw new IllegalStateException("Checkout session missing user_id metadata");
            }
            UUID userId = UUID.fromString(session.getMetadata().get("user_id"));
            User user = userRepository.findById(Objects.requireNonNull(userId))
                    .orElseThrow(() -> new IllegalStateException("User not found for checkout session"));

            if (lineItems == null || lineItems.isEmpty()) {
                throw new IllegalStateException("Checkout session has no line items");
            }

            long sumLineCents = lineItems.stream()
                    .mapToLong(li -> li.getAmountTotal() != null ? li.getAmountTotal() : 0L)
                    .sum();
            if (session.getAmountTotal() != null && sumLineCents != session.getAmountTotal()) {
                throw new IllegalStateException("Line item totals do not match session amount");
            }
            if (session.getAmountTotal() == null) {
                throw new IllegalStateException("Checkout session missing amount_total");
            }

            Map<UUID, Integer> qtyByProduct = new LinkedHashMap<>();
            Map<UUID, BigDecimal> lineTotalByProduct = new LinkedHashMap<>();
            boolean catalogIssue = false;
            for (LineItem lineItem : lineItems) {
                Product product = resolveProduct(lineItem);
                if (product.isDeleted()) {
                    // Already paid — keep the line on the order for accounting; flag for ops.
                    catalogIssue = true;
                    log.error(
                            "Soft-deleted product {} still on paid session {}. Order will be recorded for manual review.",
                            product.getId(), sessionId);
                }
                int qty = lineItem.getQuantity() != null ? lineItem.getQuantity().intValue() : 0;
                if (qty <= 0) {
                    throw new IllegalStateException("Invalid quantity for line item");
                }
                long lineCents = lineItem.getAmountTotal() != null ? lineItem.getAmountTotal() : 0L;
                BigDecimal lineTotal = StripeMoney.sgdCentsToMmk(lineCents);
                qtyByProduct.merge(product.getId(), qty, (left, right) -> left + right);
                lineTotalByProduct.merge(product.getId(), lineTotal, (left, right) -> left.add(right));
            }

            if (qtyByProduct.isEmpty()) {
                throw new IllegalStateException("No fulfillable line items on paid session " + sessionId);
            }

            boolean stockShortage = catalogIssue;
            for (Map.Entry<UUID, Integer> e : qtyByProduct.entrySet()) {
                Product fresh = productRepository.findById(Objects.requireNonNull(e.getKey()))
                        .orElseThrow(() -> new IllegalStateException("Product missing: " + e.getKey()));
                entityManager.lock(fresh, LockModeType.PESSIMISTIC_WRITE);
                if (fresh.isDeleted()) {
                    catalogIssue = true;
                    stockShortage = true;
                    log.error(
                            "Product {} became unavailable after payment. session={}",
                            fresh.getId(), sessionId);
                    continue;
                }
                if (fresh.getStock() < e.getValue()) {
                    stockShortage = true;
                    log.error(
                            "Insufficient stock after payment for product {} (need {}, have {}). "
                                    + "Order will be recorded for manual refund. session={}",
                            fresh.getId(), e.getValue(), fresh.getStock(), sessionId);
                }
            }

            BigDecimal orderTotal = StripeMoney.sgdCentsToMmk(session.getAmountTotal());

            Order order = new Order();
            order.setUser(user);
            order.setTotalPrice(orderTotal);
            // Never fail fulfillment after payment: record shortage for ops instead of rolling back the charge.
            order.setStatus(stockShortage || catalogIssue ? "PAID_STOCK_SHORTAGE" : "COMPLETED");
            order.setStripeCheckoutSessionId(sessionId);
            order = orderRepository.save(order);

            for (Map.Entry<UUID, Integer> e : qtyByProduct.entrySet()) {
                Product fresh = productRepository.findById(Objects.requireNonNull(e.getKey())).orElseThrow();
                entityManager.lock(fresh, LockModeType.PESSIMISTIC_WRITE);
                int qty = e.getValue();

                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setProduct(fresh);
                orderItem.setQuantity(qty);
                BigDecimal paidLineTotal = lineTotalByProduct.getOrDefault(e.getKey(), BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                orderItem.setPrice(paidLineTotal);
                orderItemRepository.save(orderItem);

                if (fresh.isDeleted()) {
                    continue;
                }
                if (!stockShortage) {
                    fresh.setStock(fresh.getStock() - qty);
                    productRepository.save(fresh);
                } else if (fresh.getStock() > 0) {
                    int deduct = Math.min(fresh.getStock(), qty);
                    fresh.setStock(fresh.getStock() - deduct);
                    productRepository.save(fresh);
                }
            }

            Payment payment = new Payment();
            payment.setPaymentStatus(1);
            payment.setAmount(orderTotal);
            payment.setUser(user);
            payment.setOrder(order);
            if (session.getPaymentIntent() != null) {
                payment.setStripePaymentIntentId(session.getPaymentIntent());
            }
            paymentRepository.save(payment);

            removePaidItemsFromCart(user, qtyByProduct);
        });
    }

    /** Subtract paid quantities from cart lines (do not wipe lines the user increased after checkout). */
    private void removePaidItemsFromCart(User user, Map<UUID, Integer> paidQtyByProduct) {
        Cart cart = cartRepository.findByUserWithItems(user);
        if (cart == null) {
            return;
        }
        Map<UUID, Integer> remainingPaid = new LinkedHashMap<>(paidQtyByProduct);
        Iterator<CartItem> iterator = cart.getCartItems().iterator();
        while (iterator.hasNext()) {
            CartItem item = iterator.next();
            if (item.getProduct() == null || item.getProduct().getId() == null) {
                continue;
            }
            UUID productId = item.getProduct().getId();
            Integer paidLeft = remainingPaid.get(productId);
            if (paidLeft == null || paidLeft <= 0) {
                continue;
            }
            int lineQty = item.getQuantity();
            if (lineQty <= paidLeft) {
                remainingPaid.put(productId, paidLeft - lineQty);
                iterator.remove();
            } else {
                int newQty = lineQty - paidLeft;
                remainingPaid.put(productId, 0);
                item.setQuantity(newQty);
                if (item.getProduct().getPrice() != null) {
                    item.setPrice(item.getProduct().getPrice()
                            .multiply(BigDecimal.valueOf(newQty))
                            .setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        if (cart.getCartItems().isEmpty()) {
            cartRepository.delete(cart);
            return;
        }
        BigDecimal updatedTotal = cart.getCartItems().stream()
                .map(item -> Objects.requireNonNullElse(item.getPrice(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right));
        cart.setTotalPrice(updatedTotal);
        cartRepository.save(cart);
    }

    private Product resolveProduct(LineItem lineItem) {
        Price price = lineItem.getPrice();
        if (price == null) {
            throw new IllegalStateException("Line item missing price");
        }
        if (price.getId() != null && !price.getId().isBlank()) {
            Optional<Product> byPrice = productRepository.findByStripePriceId(price.getId());
            if (byPrice.isPresent()) {
                return byPrice.get();
            }
        }
        if (price.getProduct() != null && !price.getProduct().isBlank()) {
            Optional<Product> byStripeProduct = productRepository.findByStripeProductId(price.getProduct());
            if (byStripeProduct.isPresent()) {
                return byStripeProduct.get();
            }
        }
        com.stripe.model.Product stripeProduct = price.getProductObject();
        if (stripeProduct != null && stripeProduct.getMetadata() != null) {
            String internalId = stripeProduct.getMetadata().get("internal_product_id");
            if (internalId != null && !internalId.isBlank()) {
                return productRepository.findById(Objects.requireNonNull(UUID.fromString(internalId)))
                        .orElseThrow(() -> new IllegalStateException("Unknown internal_product_id: " + internalId));
            }
        }
        throw new IllegalStateException("Could not map Stripe line item to catalog product");
    }
}
