package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePrivateConversationRequest(@NotNull UUID recipientId) { }
