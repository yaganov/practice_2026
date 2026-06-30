package ru.ulstu.soapmessenger.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dialogs")
public class Dialog {

	@Id
	@Column(name = "dialog_id", nullable = false)
	private UUID dialogId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public UUID getDialogId() {
		return dialogId;
	}

	public void setDialogId(UUID dialogId) {
		this.dialogId = dialogId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

}
