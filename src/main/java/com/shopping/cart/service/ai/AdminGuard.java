package com.shopping.cart.service.ai;

import com.shopping.cart.entity.User;
import com.shopping.cart.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Requires the JWT filter to have authenticated the request as a user with the Admin role. */
@Component
public class AdminGuard {
    private final UserRepository userRepository;

    public AdminGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireAdmin(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in as an admin.");
        }
        User user = userRepository.findByUsername(username.toString());
        if (user == null || user.getRole() == null || !"Admin".equalsIgnoreCase(user.getRole().getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }
        return user;
    }
}
