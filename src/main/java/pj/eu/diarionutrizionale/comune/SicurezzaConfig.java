package pj.eu.diarionutrizionale.comune;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Sessione su cookie, non JWT: con un backend solo e il frontend sullo
 * stesso dominio il token non compra nulla e non si revoca.
 *
 * Non c'è un bean AuthenticationManager: la verifica della password la fa
 * AutenticazioneController con il PasswordEncoder. Dichiararlo qui insieme
 * al PasswordEncoder crea anche un rischio di dipendenza circolare.
 */
@Configuration
public class SicurezzaConfig {

    @Bean
    SecurityFilterChain filtri(HttpSecurity http) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Senza questo il token CSRF è "differito": viene generato solo se
        // qualcuno lo legge, quindi il cookie XSRF-TOKEN non viene mai
        // scritto e ogni POST del frontend si prende un 403.
        csrfHandler.setCsrfRequestAttributeName(null);

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
}
