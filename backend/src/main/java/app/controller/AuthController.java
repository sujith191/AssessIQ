
package app;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.dto.LoginRequest;
import app.dto.RegisterRequest;
import app.dto.UserResponse;
import app.entity.User;
import app.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request) {

        if (request.getUsername() == null
                || request.getUsername().isBlank()
                || request.getEmail() == null
                || request.getEmail().isBlank()
                || request.getPassword() == null
                || request.getPassword().length() < 8) {

            return ResponseEntity.badRequest().body(
                    "Enter a username, email, and password of at least 8 characters.");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Username already exists.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Email already exists.");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("STUDENT");

        User saved = userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(
                        saved.getId(),
                        saved.getUsername(),
                        saved.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        if (request.getUsername() == null
                || request.getUsername().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {

            return ResponseEntity.badRequest()
                    .body("Username and password are required.");
        }

        User loggedIn = userRepository.findByUsername(
                request.getUsername()).orElse(null);

        if (loggedIn == null
                || !passwordEncoder.matches(
                        request.getPassword(),
                        loggedIn.getPassword())) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password.");
        }

        HttpSession oldSession = httpRequest.getSession(false);

        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = httpRequest.getSession(true);

        session.setAttribute("userId", loggedIn.getId());
        session.setAttribute("username", loggedIn.getUsername());
        session.setAttribute("role", loggedIn.getRole());

        return ResponseEntity.ok(new UserResponse(
                loggedIn.getId(),
                loggedIn.getUsername(),
                loggedIn.getEmail()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null
                || session.getAttribute("userId") == null) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Please log in.");
        }

        Object sessionUserId = session.getAttribute("userId");

        if (!(sessionUserId instanceof Long)) {
            session.invalidate();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid login session. Please log in again.");
        }

        Long userId = (Long) sessionUserId;

        return userRepository.findById(userId)
                .<ResponseEntity<?>>map(user -> {
                    session.setAttribute("username", user.getUsername());
                    session.setAttribute("role", user.getRole());

                    return ResponseEntity.ok(new UserResponse(
                            user.getId(),
                            user.getUsername(),
                            user.getEmail()));
                })
                .orElseGet(() -> {
                    session.invalidate();

                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("User account not found. Please log in again.");
                });
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok("Logged out successfully.");
    }
}
