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
            // Dezactivăm CSRF momentan pentru ca formularul tău vechi de register să funcționeze
            .csrf(csrf -> csrf.disable())
            
            // Lăsăm totul deschis, securitatea este gestionată manual prin HttpSession 
            // în HomeController-ul tău! Nu vrem ca Spring Security să intercepteze login-ul tău.
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
            
            // AM ȘTERS .formLogin() și .logout() ! 
            // Motivul: Când sunt activate, ele "fură" request-ul de POST /login dinspre
            // HomeController-ul tău și încearcă să valideze cu logica default de Spring, dând eroare.

        return http.build();
    }
}
