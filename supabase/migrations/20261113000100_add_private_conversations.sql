-- Private, one-to-one conversations between registered players.
CREATE TABLE private_conversations (
    conversation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_one_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    participant_two_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_private_conversation_distinct_participants CHECK (participant_one_id <> participant_two_id),
    CONSTRAINT uq_private_conversation_participants UNIQUE (participant_one_id, participant_two_id)
);

CREATE TABLE private_messages (
    message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES private_conversations(conversation_id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    content VARCHAR(2000) NOT NULL,
    read_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_private_message_content CHECK (length(trim(content)) > 0)
);

CREATE INDEX idx_private_conversations_participant_one ON private_conversations(participant_one_id, updated_at DESC);
CREATE INDEX idx_private_conversations_participant_two ON private_conversations(participant_two_id, updated_at DESC);
CREATE INDEX idx_private_messages_conversation ON private_messages(conversation_id, created_at ASC);

CREATE TRIGGER trg_private_conversations_updated_at BEFORE UPDATE ON private_conversations
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_private_messages_updated_at BEFORE UPDATE ON private_messages
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE public.private_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.private_messages ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.private_conversations, public.private_messages FROM anon, authenticated;
