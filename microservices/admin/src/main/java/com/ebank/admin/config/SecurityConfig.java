package com.ebank.admin.config;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * Secures the Admin UI with a form login (+ HTTP Basic), leaving static assets and
 * the server's own health/info open for the Docker healthcheck.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AdminServerProperties adminServer;

    public SecurityConfig(AdminServerProperties adminServer) {
        this.adminServer = adminServer;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("redirectTo");
        successHandler.setDefaultTargetUrl(adminServer.path("/"));

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(adminServer.path("/assets/**")).permitAll()
                .requestMatchers(adminServer.path("/actuator/health")).permitAll()
                .requestMatchers(adminServer.path("/actuator/info")).permitAll()
                .requestMatchers(adminServer.path("/login")).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage(adminServer.path("/login"))
                .successHandler(successHandler)
            )
            .logout(logout -> logout
                .logoutUrl(adminServer.path("/logout"))
            )
            .httpBasic(basic -> {})
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    adminServer.path("/instances"),
                    adminServer.path("/instances/**"),
                    adminServer.path("/actuator/**")
                )
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${spring.security.user.name:admin}") String username,
            @Value("${spring.security.user.password:admin}") String password) {
        return new InMemoryUserDetailsManager(
            User.withUsername(username)
                .password(passwordEncoder().encode(password))
                .roles("ADMIN")
                .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
