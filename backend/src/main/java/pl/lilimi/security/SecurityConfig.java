package pl.lilimi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;


import pl.lilimi.auth.persistence.UserAccountRepository;


@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(
  type = ConditionalOnWebApplication.Type.SERVLET
)
public class SecurityConfig {


  @Bean
  public SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    UserAccountRepository accounts
  ) throws Exception {

    http
      .addFilterAfter(
        new CredentialsVersionFilter(accounts),
        SecurityContextHolderFilter.class
      )
      .authorizeHttpRequests(authorize -> authorize
        .requestMatchers(
          HttpMethod.GET,
          "/api/products",
          "/api/products/**",
          "/actuator/health",
          "/api/auth/csrf",
          "/api/payments/attempts/*"
        ).permitAll()
        .requestMatchers(
          HttpMethod.POST,
          "/api/orders",
          "/api/orders/*/payment-session",
          "/api/orders/*/payment-session/resume",
          "/api/payments/stripe/webhook",
          "/api/project-inquiries",
          "/api/auth/register",
          "/api/auth/verify-email",
          "/api/auth/resend-verification",
          "/api/auth/request-password-reset",
          "/api/auth/reset-password"
        ).permitAll()
        .requestMatchers(
          HttpMethod.GET,
          "/api/auth/me"
        ).authenticated()
        .requestMatchers(
          HttpMethod.GET,
          "/api/account/orders",
          "/api/account/orders/**"
        ).authenticated()
        .requestMatchers(
          HttpMethod.GET,
          "/api/account/profile"
        ).hasRole("CUSTOMER")
        .requestMatchers(
          HttpMethod.PUT,
          "/api/account/profile"
        ).hasRole("CUSTOMER")
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().denyAll()
      )
      .csrf(csrf -> csrf
        .spa()
        .ignoringRequestMatchers(
          PathPatternRequestMatcher.withDefaults().matcher(
            HttpMethod.POST,
            "/api/payments/stripe/webhook"
          )
        )
      )
      .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
      )
      .requestCache(cache -> cache.disable())
      .httpBasic(AbstractHttpConfigurer::disable)
      .formLogin(form -> form
        .loginPage("/api/auth/login")
        .loginProcessingUrl("/api/auth/login")
        .usernameParameter("email")
        .passwordParameter("password")
        .successHandler((request, response, authentication) ->
          response.setStatus(204)
        )
        .failureHandler((request, response, exception) ->
          response.setStatus(401)
        )
        .permitAll()
      )
      .logout(logout -> logout
        .logoutUrl("/api/auth/logout")
        .invalidateHttpSession(true)
        .clearAuthentication(true)
        .deleteCookies("JSESSIONID")
        .logoutSuccessHandler((request, response, authentication) ->
          response.setStatus(204)
        )
        .permitAll()
      )
      .exceptionHandling(exceptions -> exceptions
        .authenticationEntryPoint((request, response, exception) ->
          response.setStatus(401)
        )
        .accessDeniedHandler((request, response, exception) ->
          response.setStatus(403)
        )
      );

    return http.build();
  }
}
