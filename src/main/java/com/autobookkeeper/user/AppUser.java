package com.autobookkeeper.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 80)
    private String ownerKey;

    @Column(nullable = false, unique = true, length = 120)
    private String apiToken;

    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String ownerKey, String apiToken, Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.ownerKey = ownerKey;
        this.apiToken = apiToken;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public String getApiToken() {
        return apiToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
