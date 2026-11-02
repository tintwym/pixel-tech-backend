package com.shopping.cart.service;

import com.shopping.cart.dto.request.CreatePromoRequest;
import com.shopping.cart.dto.response.PromoCodeResponse;
import com.shopping.cart.dto.response.PromoValidateResponse;
import com.shopping.cart.entity.PromoCode;
import com.shopping.cart.repository.PromoCodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PromoCodeService {
    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    @Transactional(readOnly = true)
    public List<PromoCodeResponse> listAll() {
        return promoCodeRepository.findAll().stream()
                .sorted(Comparator.comparing(PromoCode::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(PromoCodeService::toResponse)
                .toList();
    }

    @Transactional
    public PromoCodeResponse create(CreatePromoRequest request) {
        String code = normalize(request.getCode());
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promo code is required");
        }
        if (promoCodeRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That promo code already exists");
        }
        PromoCode.DiscountType type = request.getDiscountType();
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount type is required");
        }
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();
        if (type != PromoCode.DiscountType.FREE_SHIPPING && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        if (type == PromoCode.DiscountType.PERCENT && amount.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percent cannot exceed 100");
        }

        PromoCode promo = new PromoCode();
        promo.setCode(code);
        promo.setDiscountType(type);
        promo.setAmount(type == PromoCode.DiscountType.FREE_SHIPPING ? BigDecimal.ZERO : amount);
        promo.setActive(true);
        promo.setMaxRedemptions(request.getMaxRedemptions());
        promo.setRedemptionCount(0);
        promo.setLabel(blankToNull(request.getLabel()));
        if (request.getExpiresAt() != null && !request.getExpiresAt().isBlank()) {
            promo.setExpiresAt(LocalDateTime.parse(request.getExpiresAt().trim()));
        }
        return toResponse(promoCodeRepository.save(promo));
    }

    @Transactional
    public PromoCodeResponse setActive(UUID id, boolean active) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promo not found"));
        promo.setActive(active);
        return toResponse(promoCodeRepository.save(promo));
    }

    @Transactional(readOnly = true)
    public PromoValidateResponse validate(String rawCode) {
        String code = normalize(rawCode);
        if (code.isEmpty()) {
            return new PromoValidateResponse(false, null, null, null, null, "Enter a promo code");
        }
        return promoCodeRepository.findByCodeIgnoreCase(code)
                .map(this::validatePromo)
                .orElseGet(() -> new PromoValidateResponse(false, code, null, null, null, "This promotional code is not valid."));
    }

    private PromoValidateResponse validatePromo(PromoCode promo) {
        if (!promo.isActive()) {
            return invalid(promo, "This promo code is inactive.");
        }
        if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(LocalDateTime.now())) {
            return invalid(promo, "This promo code has expired.");
        }
        if (promo.getMaxRedemptions() != null && promo.getRedemptionCount() >= promo.getMaxRedemptions()) {
            return invalid(promo, "This promo code has reached its redemption limit.");
        }
        return new PromoValidateResponse(
                true,
                promo.getCode(),
                promo.getDiscountType().name(),
                promo.getAmount(),
                promo.getLabel(),
                "Promo applied");
    }

    private static PromoValidateResponse invalid(PromoCode promo, String message) {
        return new PromoValidateResponse(
                false,
                promo.getCode(),
                promo.getDiscountType().name(),
                promo.getAmount(),
                promo.getLabel(),
                message);
    }

    private static PromoCodeResponse toResponse(PromoCode promo) {
        return new PromoCodeResponse(
                promo.getId(),
                promo.getCode(),
                promo.getDiscountType().name(),
                promo.getAmount(),
                promo.isActive(),
                promo.getMaxRedemptions(),
                promo.getRedemptionCount(),
                promo.getExpiresAt() == null ? null : promo.getExpiresAt().toString(),
                promo.getLabel());
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
