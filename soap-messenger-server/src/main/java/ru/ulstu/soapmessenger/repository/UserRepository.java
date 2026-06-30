package ru.ulstu.soapmessenger.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.ulstu.soapmessenger.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByUsername(String username);

	Optional<User> findByUsername(String username);

}
