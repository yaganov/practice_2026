package ru.ulstu.soapmessenger.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

	@Id
	@Column(name = "message_id", nullable = false)
	private UUID messageId;

	@Column(name = "dialog_id", nullable = false)
	private UUID dialogId;

	@Column(name = "sender_id", nullable = false)
	private UUID senderId;

	@Column(name = "client_message_id", nullable = false, unique = true)
	private UUID clientMessageId;

	@Column(nullable = false)
	private String content;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public UUID getMessageId() {
		return messageId;
	}

	public void setMessageId(UUID messageId) {
		this.messageId = messageId;
	}

	public UUID getDialogId() {
		return dialogId;
	}

	public void setDialogId(UUID dialogId) {
		this.dialogId = dialogId;
	}

	public UUID getSenderId() {
		return senderId;
	}

	public void setSenderId(UUID senderId) {
		this.senderId = senderId;
	}

	public UUID getClientMessageId() {
		return clientMessageId;
	}

	public void setClientMessageId(UUID clientMessageId) {
		this.clientMessageId = clientMessageId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

}
