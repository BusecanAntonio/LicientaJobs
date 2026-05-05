package org.example.licientajobs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Dezactivăm CSRF momentan pentru ca form-urile tale vechi să meargă fără erori ascunse
            .csrf(csrf -> csrf.disable())
            
            // Permitem accesul la paginile tale custom (fără să ne blocheze Spring Security)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**", "/images/**").permitAll()
                // Orice alt request trebuie autentificat (dar momentan tu folosești HttpSession în Controller
                // așa că putem lăsa totul deschis și te bazezi pe logica ta din Controller)
                .anyRequest().permitAll()
            )
            
            // Suprascriem pagina de login default a Spring Security cu pagina ta custom
            .formLogin(form -> form
                .loginPage("/login") // Ne asigurăm că folosește login.html al tău!
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .permitAll()
            );

        return http.build();
    }
}
