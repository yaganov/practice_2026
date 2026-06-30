package ru.ulstu.soapmessenger.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.ulstu.soapmessenger.model.Dialog;

public interface DialogRepository extends JpaRepository<Dialog, UUID> {

	@Query(value = """
			SELECT d.dialog_id FROM dialogs d
			WHERE EXISTS (
				SELECT 1 FROM dialog_participants dp
				WHERE dp.dialog_id = d.dialog_id AND dp.user_id = :userId1
			)
			AND EXISTS (
				SELECT 1 FROM dialog_participants dp
				WHERE dp.dialog_id = d.dialog_id AND dp.user_id = :userId2
			)
			AND (
				SELECT COUNT(*) FROM dialog_participants dp
				WHERE dp.dialog_id = d.dialog_id
			) = 2
			LIMIT 1
			""", nativeQuery = true)
	Optional<UUID> findPersonalDialogIdBetweenUsers(@Param("userId1") UUID userId1,
			@Param("userId2") UUID userId2);

	@Modifying
	@Query(value = """
			INSERT INTO dialog_participants (dialog_id, user_id)
			VALUES (:dialogId, :userId)
			""", nativeQuery = true)
	void addParticipant(@Param("dialogId") UUID dialogId, @Param("userId") UUID userId);

}
