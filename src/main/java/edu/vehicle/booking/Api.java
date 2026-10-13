package edu.vehicle.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

record RegisterRequest(@NotBlank @Size(max=120) String fullName,@Email @NotBlank String email,@NotBlank @Size(min=8,max=72) String password,@Size(max=30) String phone){}
record LoginRequest(@Email @NotBlank String email,@NotBlank String password){}
record AuthResponse(String token,UserView user){}
record UserView(Long id,String fullName,String email,String phone,String role){}
record VehicleRequest(@NotBlank @Size(max=20) String registrationNumber,@NotBlank String make,@NotBlank String model,@Min(1950) @Max(2100) int year,@NotBlank String fuelType){}
record VehicleView(Long id,String registrationNumber,String make,String model,int year,String fuelType){}
record ServiceRequest(@NotBlank String name,@NotBlank @Size(max=1000) String description,@DecimalMin("0.01") BigDecimal price,@Min(1) @Max(1440) int estimatedDurationMinutes,boolean active){}
record ServiceView(Long id,String name,String description,BigDecimal price,int estimatedDurationMinutes,boolean active){}
record BookingRequest(@NotNull Long vehicleId,@NotNull Long serviceId,@NotNull LocalDateTime appointmentDateTime,@Size(max=1000) String customerNotes){}
record BookingView(Long id,UserView customer,VehicleView vehicle,ServiceView service,LocalDateTime appointmentDateTime,String status,String customerNotes,String adminNotes,Instant createdAt){}
record StatusRequest(@NotNull BookingStatus status,@Size(max=1000) String adminNotes){}

@RestControllerAdvice class Errors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,Object>> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("status",e.getStatusCode().value(),"error",e.getReason()==null?"Request failed":e.getReason()));}
 @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> validation(org.springframework.web.bind.MethodArgumentNotValidException e){Map<String,String> fields=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(x->fields.putIfAbsent(x.getField(),x.getDefaultMessage()));return ResponseEntity.badRequest().body(Map.of("status",400,"error","Validation failed","fields",fields));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<Map<String,Object>> conflict(){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status",409,"error","A record with those details already exists or is still in use"));}
 @ExceptionHandler(Exception.class) ResponseEntity<Map<String,Object>> generic(Exception e){return ResponseEntity.status(500).body(Map.of("status",500,"error","An unexpected server error occurred"));}
}
@Configuration class CorsConfig implements WebMvcConfigurer { @Value("${app.cors-origin}") String origins; @Override public void addCorsMappings(CorsRegistry r){r.addMapping("/api/**").allowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toArray(String[]::new)).allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS").allowedHeaders("*");} }

@RestController class HealthController { @GetMapping("/api/health") Map<String,String> health(){return Map.of("status","UP");} }

@RestController @RequestMapping("/api/auth") class AuthController {
 private final UserRepository users;private final PasswordEncoder encoder;private final TokenService tokens;
 AuthController(UserRepository users,PasswordEncoder encoder,TokenService tokens){this.users=users;this.encoder=encoder;this.tokens=tokens;}
 @PostMapping("/register") ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest r){if(users.findByEmailIgnoreCase(r.email()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered");User u=users.save(new User(r.fullName().trim(),r.email().trim().toLowerCase(),encoder.encode(r.password()),r.phone(),Role.CUSTOMER));return ResponseEntity.status(201).body(response(u));}
 @PostMapping("/login") AuthResponse login(@Valid @RequestBody LoginRequest r){User u=users.findByEmailIgnoreCase(r.email()).orElse(null);if(u==null||!encoder.matches(r.password(),u.passwordHash))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");return response(u);}
 AuthResponse response(User u){return new AuthResponse(tokens.issue(u),Views.user(u));}
}
final class Views {static UserView user(User u){return new UserView(u.id,u.fullName,u.email,u.phone,u.role.name());}static VehicleView vehicle(Vehicle v){return new VehicleView(v.id,v.registrationNumber,v.make,v.model,v.year,v.fuelType);}static ServiceView service(ServiceItem s){return new ServiceView(s.id,s.name,s.description,s.price,s.estimatedDurationMinutes,s.active);}static BookingView booking(Booking b){return new BookingView(b.id,user(b.customer),vehicle(b.vehicle),service(b.service),b.appointmentDateTime,b.status.name(),b.customerNotes,b.adminNotes,b.createdAt);}}
@org.springframework.stereotype.Component class CurrentUser {private final UserRepository users;CurrentUser(UserRepository u){users=u;}User get(Authentication a){return users.findByEmailIgnoreCase(a.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required"));}}

@RestController @RequestMapping("/api") class CustomerController {
 private final CurrentUser current;private final VehicleRepository vehicles;private final ServiceRepository services;private final BookingRepository bookings;private final UserRepository users;
 CustomerController(CurrentUser c,VehicleRepository v,ServiceRepository s,BookingRepository b,UserRepository u){current=c;vehicles=v;services=s;bookings=b;users=u;}
 @GetMapping("/me") UserView me(Authentication a){return Views.user(current.get(a));}
 @PutMapping("/me") UserView updateMe(Authentication a,@RequestBody @Valid ProfileRequest r){User u=current.get(a);u.fullName=r.fullName().trim();u.phone=r.phone();return Views.user(users.save(u));}
 @GetMapping("/vehicles") List<VehicleView> vehicles(Authentication a){return vehicles.findByOwnerId(current.get(a).id).stream().map(Views::vehicle).toList();}
 @PostMapping("/vehicles") ResponseEntity<VehicleView> addVehicle(Authentication a,@Valid @RequestBody VehicleRequest r){String registration=normalizeRegistration(r.registrationNumber());if(vehicles.existsByRegistrationNumberIgnoreCase(registration))throw new ResponseStatusException(HttpStatus.CONFLICT,"That registration number is already in use");return ResponseEntity.status(201).body(Views.vehicle(vehicles.save(new Vehicle(current.get(a),registration,r.make(),r.model(),r.year(),r.fuelType()))));}
 @PutMapping("/vehicles/{id}") VehicleView updateVehicle(Authentication a,@PathVariable Long id,@Valid @RequestBody VehicleRequest r){Vehicle v=ownedVehicle(a,id);String registration=normalizeRegistration(r.registrationNumber());if(vehicles.existsByRegistrationNumberIgnoreCaseAndIdNot(registration,id))throw new ResponseStatusException(HttpStatus.CONFLICT,"That registration number is already in use");v.registrationNumber=registration;v.make=r.make();v.model=r.model();v.year=r.year();v.fuelType=r.fuelType();return Views.vehicle(vehicles.save(v));}
 String normalizeRegistration(String registration){return registration.trim().toUpperCase(Locale.ROOT);}
 @DeleteMapping("/vehicles/{id}") ResponseEntity<Void> deleteVehicle(Authentication a,@PathVariable Long id){vehicles.delete(ownedVehicle(a,id));return ResponseEntity.noContent().build();}
 Vehicle ownedVehicle(Authentication a,Long id){return vehicles.findByIdAndOwnerId(id,current.get(a).id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Vehicle not found"));}
 @GetMapping("/services") List<ServiceView> catalogue(){return services.findByActiveTrue().stream().map(Views::service).toList();}
 @PostMapping("/bookings") ResponseEntity<BookingView> create(Authentication a,@Valid @RequestBody BookingRequest r){User customer=current.get(a);Vehicle vehicle=ownedVehicle(a,r.vehicleId());ServiceItem service=services.findById(r.serviceId()).filter(s->s.active).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Service not found"));if(!r.appointmentDateTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Appointment must be in the future (UTC)");if(bookings.existsByVehicleIdAndAppointmentDateTimeAndStatusNot(vehicle.id,r.appointmentDateTime(),BookingStatus.CANCELLED))throw new ResponseStatusException(HttpStatus.CONFLICT,"This vehicle already has an appointment at that time");Booking b=new Booking();b.customer=customer;b.vehicle=vehicle;b.service=service;b.appointmentDateTime=r.appointmentDateTime();b.customerNotes=r.customerNotes();return ResponseEntity.status(201).body(Views.booking(bookings.save(b)));}
 @GetMapping("/bookings") List<BookingView> mine(Authentication a){return bookings.findByCustomerIdOrderByAppointmentDateTimeDesc(current.get(a).id).stream().map(Views::booking).toList();}
 @GetMapping("/bookings/{id}") BookingView detail(Authentication a,@PathVariable Long id){return Views.booking(ownedBooking(a,id));}
 @PostMapping("/bookings/{id}/cancel") BookingView cancel(Authentication a,@PathVariable Long id){Booking b=ownedBooking(a,id);if(b.status!=BookingStatus.PENDING&&b.status!=BookingStatus.CONFIRMED)throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking can no longer be cancelled");b.status=BookingStatus.CANCELLED;return Views.booking(bookings.save(b));}
 Booking ownedBooking(Authentication a,Long id){User actor=current.get(a);if(actor.role==Role.ADMIN)return bookings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));return bookings.findByIdAndCustomerId(id,actor.id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));}
}
record ProfileRequest(@NotBlank @Size(max=120) String fullName,@Size(max=30) String phone){}

@RestController @RequestMapping("/api/admin") class AdminController {
 private final BookingRepository bookings;private final ServiceRepository services;private final UserRepository users;
 AdminController(BookingRepository b,ServiceRepository s,UserRepository u){bookings=b;services=s;users=u;}
 @GetMapping("/summary") Map<String,Long> summary(){Map<String,Long> out=new LinkedHashMap<>();out.put("total",bookings.count());for(BookingStatus s:BookingStatus.values())out.put(s.name().toLowerCase(),bookings.findAll().stream().filter(b->b.status==s).count());out.put("customers",users.findAll().stream().filter(u->u.role==Role.CUSTOMER).count());return out;}
 @GetMapping("/bookings") List<BookingView> all(@RequestParam(required=false) BookingStatus status,@RequestParam(required=false) String search){return bookings.findAll().stream().filter(b->status==null||b.status==status).filter(b->search==null||b.customer.fullName.toLowerCase().contains(search.toLowerCase())||b.vehicle.registrationNumber.toLowerCase().contains(search.toLowerCase())).sorted(Comparator.comparing((Booking b)->b.appointmentDateTime).reversed()).map(Views::booking).toList();}
 @PatchMapping("/bookings/{id}/status") BookingView setStatus(@PathVariable Long id,@Valid @RequestBody StatusRequest r){Booking b=bookings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));boolean valid=switch(b.status){case PENDING->r.status()==BookingStatus.CONFIRMED||r.status()==BookingStatus.CANCELLED||r.status()==BookingStatus.REJECTED;case CONFIRMED->r.status()==BookingStatus.IN_PROGRESS||r.status()==BookingStatus.CANCELLED;case IN_PROGRESS->r.status()==BookingStatus.COMPLETED;default->false;};if(!valid)throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid booking status transition");b.status=r.status();b.adminNotes=r.adminNotes();return Views.booking(bookings.save(b));}
 @GetMapping("/services") List<ServiceView> allServices(){return services.findAll().stream().map(Views::service).toList();}
 @PostMapping("/services") ResponseEntity<ServiceView> add(@Valid @RequestBody ServiceRequest r){ServiceItem s=new ServiceItem();apply(s,r);return ResponseEntity.status(201).body(Views.service(services.save(s)));}
 @PutMapping("/services/{id}") ServiceView edit(@PathVariable Long id,@Valid @RequestBody ServiceRequest r){ServiceItem s=services.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Service not found"));apply(s,r);return Views.service(services.save(s));}
 @DeleteMapping("/services/{id}") ResponseEntity<Void> delete(@PathVariable Long id){ServiceItem s=services.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Service not found"));if(bookings.findAll().stream().anyMatch(b->b.service.id.equals(id)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Service is used by bookings; deactivate it instead");services.delete(s);return ResponseEntity.noContent().build();}
 @GetMapping("/users") List<UserView> users(){return users.findAll().stream().map(Views::user).toList();}
 private void apply(ServiceItem s,ServiceRequest r){s.name=r.name().trim();s.description=r.description();s.price=r.price();s.estimatedDurationMinutes=r.estimatedDurationMinutes();s.active=r.active();}
}
