package com.eclaims.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "EN_TBL_PASSWORD_RESET_TOKEN")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "StaffID", nullable = false, length = 50)
    private String staffId;

    @Column(name = "Token", nullable = false, unique = true)
    private String token;

    @Column(name = "ExpiryDate", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "Used", nullable = false)
    private boolean used;

    @Column(name = "DateCreated", nullable = false)
    private LocalDateTime dateCreated;
}