package edu.vehicle.booking;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

enum Role { CUSTOMER, ADMIN }
enum BookingStatus { PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, REJECTED }

@Entity @Table(name="users") class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(name="full_name",nullable=false) String fullName;
 @Column(nullable=false,unique=true) String email;
 @Column(name="password_hash",nullable=false) String passwordHash;
 String phone;
 @Enumerated(EnumType.STRING) @Column(nullable=false) Role role=Role.CUSTOMER;
 @Column(name="created_at",updatable=false) Instant createdAt=Instant.now();
 protected User() {}
 User(String name,String email,String hash,String phone,Role role){this.fullName=name;this.email=email;this.passwordHash=hash;this.phone=phone;this.role=role;}
}
@Entity @Table(name="vehicles") class Vehicle {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @ManyToOne(optional=false) @JoinColumn(name="owner_id") User owner;
 @Column(name="registration_number",nullable=false,unique=true) String registrationNumber;
 @Column(nullable=false) String make; @Column(nullable=false) String model; @Column(name="vehicle_year",nullable=false) int year;
 @Column(name="fuel_type",nullable=false) String fuelType; @Column(name="created_at",updatable=false) Instant createdAt=Instant.now();
 protected Vehicle() {}
 Vehicle(User owner,String reg,String make,String model,int year,String fuel){this.owner=owner;this.registrationNumber=reg;this.make=make;this.model=model;this.year=year;this.fuelType=fuel;}
}
@Entity @Table(name="services") class ServiceItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false,unique=true) String name; @Column(nullable=false,length=1000) String description;
 @Column(nullable=false,precision=10,scale=2) BigDecimal price;
 @Column(name="estimated_duration_minutes",nullable=false) int estimatedDurationMinutes;
 @Column(nullable=false) boolean active=true;
 protected ServiceItem() {}
}
@Entity @Table(name="bookings") class Booking {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @ManyToOne(optional=false) @JoinColumn(name="customer_id") User customer;
 @ManyToOne(optional=false) @JoinColumn(name="vehicle_id") Vehicle vehicle;
 @ManyToOne(optional=false) @JoinColumn(name="service_id") ServiceItem service;
 @Column(name="appointment_date_time",nullable=false) LocalDateTime appointmentDateTime;
 @Enumerated(EnumType.STRING) @Column(nullable=false) BookingStatus status=BookingStatus.PENDING;
 @Column(name="customer_notes",length=1000) String customerNotes; @Column(name="admin_notes",length=1000) String adminNotes;
 @Column(name="created_at",updatable=false) Instant createdAt=Instant.now(); @Column(name="updated_at") Instant updatedAt=Instant.now();
 protected Booking() {}
 @PreUpdate void touch(){updatedAt=Instant.now();}
}
