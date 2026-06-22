package org.example.licientajobs;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@SpringBootApplication
public class LicientaJobsApplication {

    private static final Logger logger = LoggerFactory.getLogger(LicientaJobsApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LicientaJobsApplication.class, args);
    }

    @Bean
    public CommandLineRunner encryptExistingPasswords(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            logger.info("Starting password encryption migration...");
            try {
                List<User> users = userRepository.findAll();
                for (User user : users) {
                    // Check if password is NOT already BCrypt encrypted
                    // BCrypt hashes typically start with $2a$, $2b$, or $2y$
                    if (user.getPassword() != null &&
                        !user.getPassword().startsWith("$2a$") &&
                        !user.getPassword().startsWith("$2b$") &&
                        !user.getPassword().startsWith("$2y$")) {

                        String plainPassword = user.getPassword(); // Get the current plain-text password
                        String encodedPassword = passwordEncoder.encode(plainPassword);
                        user.setPassword(encodedPassword);
                        userRepository.save(user); // Save the user with the new encrypted password
                        logger.info("User '{}' password encrypted and updated in Memgraph.", user.getUsername());
                    }
                }
                logger.info("Password encryption migration completed.");
            } catch (Exception e) {
                logger.error("Error during password encryption migration: {}", e.getMessage(), e);
            }
        };
    }
}
