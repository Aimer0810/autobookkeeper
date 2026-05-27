package com.autobookkeeper.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByApiToken(String apiToken);

    boolean existsByUsername(String username);

    boolean existsByOwnerKey(String ownerKey);

    boolean existsByApiToken(String apiToken);
}
