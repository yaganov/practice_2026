package ru.ulstu.soapmessenger.repository;

import java.util.List;
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

	@Query(value = """
			SELECT d.dialog_id, d.created_at, u.user_id, u.username, lm.content, lm.created_at
			FROM dialogs d
			JOIN dialog_participants dp_current
				ON d.dialog_id = dp_current.dialog_id AND dp_current.user_id = :currentUserId
			JOIN dialog_participants dp_other
				ON d.dialog_id = dp_other.dialog_id AND dp_other.user_id <> :currentUserId
			JOIN users u ON u.user_id = dp_other.user_id
			LEFT JOIN LATERAL (
				SELECT m.content, m.created_at
				FROM messages m
				WHERE m.dialog_id = d.dialog_id
				ORDER BY m.created_at DESC, m.message_id DESC
				LIMIT 1
			) lm ON TRUE
			WHERE (
				SELECT COUNT(*) FROM dialog_participants dp
				WHERE dp.dialog_id = d.dialog_id
			) = 2
			ORDER BY
				CASE WHEN lm.created_at IS NULL THEN 1 ELSE 0 END,
				lm.created_at DESC,
				d.created_at DESC,
				d.dialog_id
			""", nativeQuery = true)
	List<Object[]> findPersonalDialogsWithInterlocutor(@Param("currentUserId") UUID currentUserId);

	@Query(value = """
			SELECT EXISTS (
				SELECT 1 FROM dialog_participants
				WHERE dialog_id = :dialogId AND user_id = :userId
			)
			""", nativeQuery = true)
	boolean isParticipant(@Param("dialogId") UUID dialogId, @Param("userId") UUID userId);

	@Query(value = """
			SELECT user_id FROM dialog_participants
			WHERE dialog_id = :dialogId
			""", nativeQuery = true)
	List<UUID> findParticipantIdsByDialogId(@Param("dialogId") UUID dialogId);

}
