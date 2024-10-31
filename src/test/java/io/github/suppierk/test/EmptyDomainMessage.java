package io.github.suppierk.test;

import io.github.suppierk.ddd.cqrs.DomainMessage;
import java.io.Serial;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

/** A sample instance of {@link DomainMessage} for tests. */
public final class EmptyDomainMessage implements DomainMessage<UUID, Instant> {
  @Serial private static final long serialVersionUID = 3809373667732226284L;

  private final UUID id;
  private final Instant createdAt;

  private EmptyDomainMessage() {
    this.id = UUID.randomUUID();
    this.createdAt = Instant.now();
  }

  @Override
  public UUID messageId() {
    return id;
  }

  @Override
  public Instant createdAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EmptyDomainMessage that = (EmptyDomainMessage) o;
    return id.equals(that.id) && createdAt.equals(that.createdAt);
  }

  @Override
  public int hashCode() {
    int result = id.hashCode();
    result = 31 * result + createdAt.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", EmptyDomainMessage.class.getSimpleName() + "[", "]")
        .add("id=" + id)
        .add("createdAt=" + createdAt)
        .toString();
  }

  public static DomainMessage<UUID, Instant> getInstance() {
    return Holder.INSTANCE;
  }

  private static class Holder {
    private static final EmptyDomainMessage INSTANCE = new EmptyDomainMessage();
  }
}
