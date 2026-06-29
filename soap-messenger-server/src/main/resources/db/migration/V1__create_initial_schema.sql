CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE dialogs (
    dialog_id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE dialog_participants (
    dialog_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (dialog_id, user_id),
    FOREIGN KEY (dialog_id) REFERENCES dialogs (dialog_id),
    FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE messages (
    message_id UUID PRIMARY KEY,
    dialog_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    client_message_id UUID NOT NULL UNIQUE,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (dialog_id) REFERENCES dialogs (dialog_id),
    FOREIGN KEY (dialog_id, sender_id) REFERENCES dialog_participants (dialog_id, user_id),
    CONSTRAINT messages_content_not_blank CHECK (content <> '' AND content !~ '^\s*$')
);

CREATE INDEX idx_dialog_participants_user_id ON dialog_participants (user_id);

CREATE INDEX idx_messages_dialog_id_created_at ON messages (dialog_id, created_at);
