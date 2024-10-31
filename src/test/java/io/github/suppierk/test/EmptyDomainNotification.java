package io.github.suppierk.test;

import io.github.suppierk.ddd.async.DomainNotification;
import java.time.Instant;
import java.util.UUID;

public record EmptyDomainNotification(UUID messageId, Instant createdAt)
    implements DomainNotification<UUID, Instant> {
  public EmptyDomainNotification() {
    this(UUID.randomUUID(), Instant.now());
  }
}
