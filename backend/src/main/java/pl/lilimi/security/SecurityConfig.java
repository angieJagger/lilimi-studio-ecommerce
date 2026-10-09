package pl.lilimi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;


@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(
  type = ConditionalOnWebApplication.Type.SERVLET
)
public class SecurityConfig {


  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http)
    throws Exception {

    http
      .authorizeHttpRequests(authorize -> authorize
        .requestMatchers(
          HttpMethod.GET,
          "/api/products",
          "/api/products/**",
          "/actuator/health",
          "/api/auth/csrf"
        ).permitAll()
        .requestMatchers(
          HttpMethod.POST,
          "/api/orders",
          "/api/project-inquiries"
        ).permitAll()
        .requestMatchers(
          HttpMethod.GET,
          "/api/auth/me"
        ).authenticated()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().denyAll()
      )
      .csrf(csrf -> csrf.spa())
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
