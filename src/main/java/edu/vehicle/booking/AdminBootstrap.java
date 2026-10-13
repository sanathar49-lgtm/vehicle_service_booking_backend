package edu.vehicle.booking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration class AdminBootstrap {
 @Bean CommandLineRunner bootstrapAdmin(UserRepository users,PasswordEncoder encoder,@Value("${app.admin-email:}") String email,@Value("${app.admin-password:}") String password){return args->{if(email.isBlank()&&password.isBlank())return;if(email.isBlank()||password.length()<12)throw new IllegalStateException("Set both ADMIN_EMAIL and ADMIN_PASSWORD (minimum 12 characters)");users.findByEmailIgnoreCase(email).orElseGet(()->users.save(new User("System Administrator",email.toLowerCase(),encoder.encode(password),null,Role.ADMIN)));};}
}
