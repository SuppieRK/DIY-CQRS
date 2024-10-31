/*
 * This file is generated by jOOQ.
 */
package io.github.suppierk.test.example.tables.records;

import io.github.suppierk.test.example.tables.TestTable;
import java.time.LocalDateTime;
import java.util.UUID;
import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;

/** This class is generated by jOOQ. */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class TestTableRecord extends UpdatableRecordImpl<TestTableRecord> {

  private static final long serialVersionUID = 1L;

  /** Setter for <code>public.test.id</code>. */
  public TestTableRecord setId(UUID value) {
    set(0, value);
    return this;
  }

  /** Getter for <code>public.test.id</code>. */
  public UUID getId() {
    return (UUID) get(0);
  }

  /** Setter for <code>public.test.created_at</code>. */
  public TestTableRecord setCreatedAt(LocalDateTime value) {
    set(1, value);
    return this;
  }

  /** Getter for <code>public.test.created_at</code>. */
  public LocalDateTime getCreatedAt() {
    return (LocalDateTime) get(1);
  }

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  @Override
  public Record1<UUID> key() {
    return (Record1) super.key();
  }

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /** Create a detached TestRecord */
  public TestTableRecord() {
    super(TestTable.TEST_TABLE);
  }

  /** Create a detached, initialised TestRecord */
  public TestTableRecord(UUID id, LocalDateTime createdAt) {
    super(TestTable.TEST_TABLE);

    setId(id);
    setCreatedAt(createdAt);
    resetChangedOnNotNull();
  }
}