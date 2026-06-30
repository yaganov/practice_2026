package ru.ulstu.soapmessenger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.ulstu.soapmessenger.model.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	List<Message> findByDialogIdOrderByCreatedAtAsc(UUID dialogId);

}
