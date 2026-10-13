package edu.vehicle.booking;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Configuration
class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecretKey jwtKey(@Value("${app.jwt-secret}") String secret){if(secret.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));}
 @Bean TokenService tokenService(SecretKey key,@Value("${app.jwt-expiration-ms}") long expiration){return new TokenService(key,expiration);}
 @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origin}") String origins){
  CorsConfiguration configuration=new CorsConfiguration();
  configuration.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(origin->!origin.isEmpty()).toList());
  configuration.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
  configuration.setAllowedHeaders(List.of("Authorization","Content-Type"));
  UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
  source.registerCorsConfiguration("/**",configuration);
  return source;
 }
 @Bean SecurityFilterChain filterChain(HttpSecurity http,JwtFilter filter) throws Exception {return http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(HttpServletResponse.SC_UNAUTHORIZED))).authorizeHttpRequests(a->a.requestMatchers("/api/health","/api/auth/**","/api/services").permitAll().requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated()).addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();}
}
@Component class JwtFilter extends OncePerRequestFilter {
 private final TokenService tokens; private final UserRepository users;
 JwtFilter(TokenService tokens,UserRepository users){this.tokens=tokens;this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
  String h=req.getHeader(HttpHeaders.AUTHORIZATION);
  if(h!=null&&h.startsWith("Bearer "))try{String email=tokens.subject(h.substring(7));User u=users.findByEmailIgnoreCase(email).orElse(null);if(u!=null){var auth=new UsernamePasswordAuthenticationToken(u.email,null,java.util.List.of(new SimpleGrantedAuthority("ROLE_"+u.role)));org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);}}catch(RuntimeException ignored){}
  chain.doFilter(req,res);
 }
}
@org.springframework.stereotype.Service class TokenService {
 private final SecretKey key; private final long expiration;
 TokenService(SecretKey key,long expiration){this.key=key;this.expiration=expiration;}
 String issue(User u){long now=System.currentTimeMillis();return Jwts.builder().subject(u.email).claim("role",u.role.name()).issuedAt(new Date(now)).expiration(new Date(now+expiration)).signWith(key).compact();}
 String subject(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
}
