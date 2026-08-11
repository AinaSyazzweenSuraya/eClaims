package com.eclaims.config;

import com.eclaims.repository.UserAccountRepository;
import com.eclaims.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final UserAccountRepository userAccountRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        http
            .csrf(csrf -> csrf.csrfTokenRequestHandler(csrfHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**","/js/**","/images/**","/webjars/**").permitAll()
                .requestMatchers("/login","/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/finance/**").hasAnyRole("FINANCE","ADMIN")
                .requestMatchers("/approval/superior/**").hasAnyRole("SUPERIOR","ADMIN")
                .requestMatchers("/approval/pm/**").hasAnyRole("MANAGER","SUPERIOR","ADMIN")
                .requestMatchers("/forgot-password", "/reset-password").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(s -> s.maximumSessions(1));

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            log.info("=== LOGIN ATTEMPT: username=[{}] ===", username);
            try {
                UserAccount ua = userAccountRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        log.error("USER NOT FOUND in DB: [{}]", username);
                        return new UsernameNotFoundException("User not found: " + username);
                    });

                log.info("Found user: id={}, username={}, role={}, isActive={}",
                    ua.getUserId(), ua.getUsername(), ua.getRole(), ua.getIsActive());
                log.info("Stored password hash: [{}]", ua.getPassword());

                if (ua.getIsActive() == null || !ua.getIsActive()) {
                    log.error("Account is inactive: {}", username);
                    throw new UsernameNotFoundException("Account inactive: " + username);
                }

                UserDetails details = new User(
                    ua.getUsername(),
                    ua.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + ua.getRole().name()))
                );
                log.info("UserDetails created successfully for: {}", username);
                return details;

            } catch (UsernameNotFoundException e) {
                throw e;
            } catch (Exception e) {
                log.error("UNEXPECTED ERROR loading user [{}]: {}", username, e.getMessage(), e);
                throw new UsernameNotFoundException("Error loading user: " + username, e);
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
