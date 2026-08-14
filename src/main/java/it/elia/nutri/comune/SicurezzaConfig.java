package it.elia.nutri.comune;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Sessione su cookie, non JWT: con un backend solo e il frontend
 * sullo stesso dominio il token non compra nulla e non si revoca.
 *
 * Il cookie di sessione è HttpOnly e SameSite=Lax (application.yml).
 * Il token CSRF sta in un cookie leggibile da JavaScript, che il
 * frontend rimanda nell'header X-XSRF-TOKEN: è lo schema previsto
 * per le single page application.
 */
@Configuration
public class SicurezzaConfig {

    @Bean
    SecurityFilterChain filtri(HttpSecurity http) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();

        return http
            .csrf(c -> c
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/api/auth/registrazione", "/api/auth/accesso"))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/app.js", "/app.css",
                                 "/manifest.json", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/registrazione", "/api/auth/accesso").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            // Senza questo, una chiamata non autenticata risponde 302 verso
            // una pagina di login che non esiste. Il frontend vuole un 401.
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .logout(l -> l.disable())
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Non è un bean di default: serve al controller di accesso. */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }
}
