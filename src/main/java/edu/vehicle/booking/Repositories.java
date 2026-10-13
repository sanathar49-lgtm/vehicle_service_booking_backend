package edu.vehicle.booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
interface UserRepository extends JpaRepository<User,Long>{Optional<User> findByEmailIgnoreCase(String email);}
interface VehicleRepository extends JpaRepository<Vehicle,Long>{List<Vehicle> findByOwnerId(Long id);Optional<Vehicle> findByIdAndOwnerId(Long id,Long owner);boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);boolean existsByRegistrationNumberIgnoreCaseAndIdNot(String registrationNumber,Long id);}
interface ServiceRepository extends JpaRepository<ServiceItem,Long>{List<ServiceItem> findByActiveTrue();}
interface BookingRepository extends JpaRepository<Booking,Long>{List<Booking> findByCustomerIdOrderByAppointmentDateTimeDesc(Long id);Optional<Booking> findByIdAndCustomerId(Long id,Long customer);boolean existsByVehicleIdAndAppointmentDateTimeAndStatusNot(Long vehicleId,LocalDateTime time,BookingStatus status);}
