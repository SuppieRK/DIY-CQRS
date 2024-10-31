package io.github.suppierk.ddd.cqrs;

import static io.github.suppierk.test.example.Tables.TEST_TABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.suppierk.ddd.async.DomainNotification;
import io.github.suppierk.ddd.async.DomainNotificationProducer;
import io.github.suppierk.ddd.authorization.AnonymousDomainClient;
import io.github.suppierk.ddd.authorization.DomainClient;
import io.github.suppierk.ddd.authorization.UnauthorizedException;
import io.github.suppierk.test.AnotherDomainClient;
import io.github.suppierk.test.EmptyDomainNotification;
import io.github.suppierk.test.example.tables.records.TestTableRecord;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.exception.DetachedException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DomainCommandHandlerTest {
  static final Condition NO_CONDITION = DSL.noCondition();

  static final AtomicBoolean NOTIFICATION_DELIVERY_REQUESTED;
  static final DomainNotificationProducer NOTIFICATION_PRODUCER;
  static final DSLContext DSL_CONTEXT;

  static {
    NOTIFICATION_DELIVERY_REQUESTED = new AtomicBoolean(false);

    NOTIFICATION_PRODUCER =
        new DomainNotificationProducer() {
          @Override
          public <E extends DomainNotification<?, ?>> void store(
              DSLContext readWriteDsl, E notification) {
            NOTIFICATION_DELIVERY_REQUESTED.set(true);
          }
        };

    try {
      final var connection =
          DriverManager.getConnection(
              "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
      DSL_CONTEXT = DSL.using(connection, SQLDialect.POSTGRES);

      final var createTestTableSql =
          String.join(
              "\n",
              Files.readAllLines(
                  Paths.get(
                      DomainCommandHandlerTest.class
                          .getClassLoader()
                          .getResource("test_table.sql")
                          .toURI())));

      connection.prepareStatement(createTestTableSql).execute();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeEach
  void setUp() {
    NOTIFICATION_DELIVERY_REQUESTED.set(false);
    DSL_CONTEXT.truncate(TEST_TABLE).execute();
  }

  @Test
  void decapitalize_should_produce_correct_results() {
    final var capitalized = "Capitalized";
    final var decapitalized = "capitalized";

    final Create.TestHandler handler = new Create.TestHandler(Create.TestDomainCommand.class);

    assertEquals(decapitalized, handler.decapitalize(capitalized));
    assertEquals(decapitalized, handler.decapitalize(decapitalized));
  }

  @Nested
  class Create {
    static final TestHandler HANDLER = new TestHandler(TestDomainCommand.class);
    static final TestDomainCommand COMMAND = new TestDomainCommand();

    @Test
    void when_command_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(null));
    }

    @Test
    void when_command_class_is_present_it_must_be_not_null() {
      final var handler = assertDoesNotThrow(() -> new TestHandler(TestDomainCommand.class));
      assertNotNull(handler.getCommandClass());
    }

    @Test
    void when_command_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  null, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_command_client_is_null_illegal_state_exception_is_thrown() {
      final var command = new TestDomainCommand(null);

      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_client_is_not_authorized_unauthorized_exception_must_be_thrown() {
      final var command = new TestDomainCommand(AnotherDomainClient.getInstance());

      assertThrows(
          UnauthorizedException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_write_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, null, DSL_CONTEXT, TEST_TABLE, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_only_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, DSL_CONTEXT, null, TEST_TABLE, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_table_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, DSL_CONTEXT, DSL_CONTEXT, null, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_condition_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, null, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_notification_producer_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, NO_CONDITION, null));
    }

    @Test
    void when_run_result_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_success_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullSuccessNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));

      assertEquals(
          0,
          DSL_CONTEXT.selectCount().from(TEST_TABLE).fetchOne(0, int.class),
          "Despite success, failure to deliver notification must revert INSERT operation within transaction");
    }

    @Test
    void when_optional_failure_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullFailureNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_all_parameters_were_passed_the_output_must_not_be_null() {
      assertNotNull(
          HANDLER.runInContext(
              COMMAND, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_no_failures_occurred_the_on_success_method_must_be_triggered() {
      assertDoesNotThrow(
          () ->
              HANDLER.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
      assertTrue(HANDLER.onSuccessInvoked.get(), "Expected to invoke onSuccess");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_failure_occurred_the_on_failure_method_must_be_triggered() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          Exception.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
      assertTrue(handler.onFailureInvoked.get(), "Expected to invoke onFailure");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_user_attempts_to_trigger_database_operation_in_run_exception_is_thrown() {
      final var handler = new TestDatabaseReachingHandler();

      assertThrows(
          DetachedException.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));

      final var cheatingHandler = new TestDatabaseCheatingHandler();

      final var savedRecord =
          assertDoesNotThrow(
              () ->
                  cheatingHandler.runInContext(
                      COMMAND,
                      DSL_CONTEXT,
                      DSL_CONTEXT,
                      TEST_TABLE,
                      NO_CONDITION,
                      NOTIFICATION_PRODUCER));

      assertDoesNotThrow(
          () -> savedRecord.refresh(), "Record must be reattached to DSL after execution");
    }

    /** Test command implementation. */
    record TestDomainCommand(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainCommand.Create<UUID, Instant> {
      TestDomainCommand(DomainClient domainClient) {
        this(UUID.randomUUID(), Instant.now(), domainClient);
      }

      TestDomainCommand() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test handler implementation. */
    static class TestHandler
        extends DomainCommandHandler.Create<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onSuccessInvoked;

      TestHandler(Class<TestDomainCommand> commandClass) {
        super(commandClass);
        this.onSuccessInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, TestTableRecord testTableRecord) {
        onSuccessInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected TestTableRecord fillBlankRecord(
          TestDomainCommand command, TestTableRecord blankRecord) {
        onSuccessInvoked.set(false); // Because run invoked before, we can reset the value here
        blankRecord.setId(UUID.randomUUID());
        blankRecord.setCreatedAt(LocalDateTime.now());
        return blankRecord;
      }
    }

    /** Test handler implementation which tries to invoke DSL operation on the record. */
    static class TestDatabaseReachingHandler
        extends DomainCommandHandler.Create<TestDomainCommand, TestTableRecord> {
      TestDatabaseReachingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected TestTableRecord fillBlankRecord(
          TestDomainCommand command, TestTableRecord blankRecord) {
        blankRecord.setId(UUID.randomUUID());
        blankRecord.setCreatedAt(LocalDateTime.now());
        blankRecord.store(); // Imitating user's action to store the detached record
        return blankRecord;
      }
    }

    /** Test handler implementation which tries to detach DSL from the record. */
    static class TestDatabaseCheatingHandler
        extends DomainCommandHandler.Create<TestDomainCommand, TestTableRecord> {
      TestDatabaseCheatingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected TestTableRecord fillBlankRecord(
          TestDomainCommand command, TestTableRecord blankRecord) {
        blankRecord.setId(UUID.randomUUID());
        blankRecord.setCreatedAt(LocalDateTime.now());
        blankRecord.detach();
        return blankRecord;
      }
    }

    /**
     * Test handler implementation where {@link
     * DomainCommandHandler#internalRunContract(DomainCommand, DSLContext, DSLContext, Table,
     * Condition, DomainNotificationProducer)} returns {@code null}.
     */
    static class TestNullRunHandler
        extends DomainCommandHandler.Create<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onFailureInvoked;

      TestNullRunHandler() {
        super(TestDomainCommand.class);
        this.onFailureInvoked = new AtomicBoolean(false);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        onFailureInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected TestTableRecord fillBlankRecord(
          TestDomainCommand command, TestTableRecord blankRecord) {
        onFailureInvoked.set(false); // Because run invoked before, we can reset the value here
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onSuccess(Object, Object)} returns
     * {@code null}.
     */
    static class TestNullSuccessNotificationHandler
        extends DomainCommandHandler.Create<TestDomainCommand, TestTableRecord> {
      TestNullSuccessNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, TestTableRecord testTableRecord) {
        return null;
      }

      @Override
      protected TestTableRecord fillBlankRecord(
          TestDomainCommand command, TestTableRecord blankRecord) {
        blankRecord.setId(UUID.randomUUID());
        blankRecord.setCreatedAt(LocalDateTime.now());
        return blankRecord;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onFailure(Object, Throwable)} returns
     * {@code null}.
     */
    static class TestNullFailureNotificationHandler
        extends DomainCommandHandler.Create<TestDomainCommand, TestTableRecord> {
      TestNullFailureNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        return null;
      }

      @Override
      protected TestTableRecord fillBlankRecord(
          TestDomainCommand command, TestTableRecord blankRecord) {
        return null; // To trigger error
      }
    }
  }

  @Nested
  class Update {
    static final TestHandler HANDLER = new TestHandler(TestDomainCommand.class);

    LocalDateTime testOriginalValue;
    TestTableRecord testRecord;
    TestDomainCommand testCommand;
    Condition testCondition;

    @BeforeEach
    void setUp() {
      testOriginalValue = LocalDateTime.now();

      testRecord = DSL_CONTEXT.newRecord(TEST_TABLE);
      testRecord.setId(UUID.randomUUID());
      testRecord.setCreatedAt(testOriginalValue);
      DSL_CONTEXT.batchInsert(testRecord).execute();

      testCondition = TEST_TABLE.ID.eq(testRecord.getId());

      testCommand = new TestDomainCommand(testCondition);
    }

    @Test
    void when_command_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(null));
    }

    @Test
    void when_command_class_is_present_it_must_be_not_null() {
      final var handler = assertDoesNotThrow(() -> new TestHandler(TestDomainCommand.class));
      assertNotNull(handler.getCommandClass());
    }

    @Test
    void when_command_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  null,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_command_client_is_null_illegal_state_exception_is_thrown() {
      final var command = new TestDomainCommand((DomainClient) null);

      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_client_is_not_authorized_unauthorized_exception_must_be_thrown() {
      final var command = new TestDomainCommand(AnotherDomainClient.getInstance());

      assertThrows(
          UnauthorizedException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_write_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  null,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_only_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  null,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_table_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  null,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_condition_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  testCommand, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, null, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_notification_producer_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, testCondition, null));
    }

    @Test
    void when_run_result_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_success_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullSuccessNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));

      final var databaseState = DSL_CONTEXT.selectFrom(TEST_TABLE).where(testCondition).fetchOne();
      assertNotNull(databaseState);
      assertEquals(
          testOriginalValue.getYear(),
          databaseState.getCreatedAt().getYear(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getMonth(),
          databaseState.getCreatedAt().getMonth(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getDayOfMonth(),
          databaseState.getCreatedAt().getDayOfMonth(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getHour(),
          databaseState.getCreatedAt().getHour(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getMinute(),
          databaseState.getCreatedAt().getMinute(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getSecond(),
          databaseState.getCreatedAt().getSecond(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
    }

    @Test
    void when_optional_failure_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullFailureNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_all_parameters_were_passed_the_output_must_not_be_null() {
      assertNotNull(
          HANDLER.runInContext(
              testCommand,
              DSL_CONTEXT,
              DSL_CONTEXT,
              TEST_TABLE,
              testCondition,
              NOTIFICATION_PRODUCER));
    }

    @Test
    void when_no_failures_occurred_the_on_success_method_must_be_triggered() {
      assertDoesNotThrow(
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
      assertTrue(HANDLER.onSuccessInvoked.get(), "Expected to invoke onSuccess");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_failure_occurred_the_on_failure_method_must_be_triggered() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          Exception.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
      assertTrue(handler.onFailureInvoked.get(), "Expected to invoke onFailure");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_user_attempts_to_trigger_database_operation_in_run_exception_is_thrown() {
      final var handler = new TestDatabaseReachingHandler();

      assertThrows(
          DetachedException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));

      final var cheatingHandler = new TestDatabaseCheatingHandler();

      final var updatedRecord =
          assertDoesNotThrow(
              () ->
                  cheatingHandler.runInContext(
                      testCommand,
                      DSL_CONTEXT,
                      DSL_CONTEXT,
                      TEST_TABLE,
                      testCondition,
                      NOTIFICATION_PRODUCER));

      assertTrue(updatedRecord.isPresent());
      assertDoesNotThrow(
          () -> updatedRecord.get().refresh(), "Record must be reattached to DSL after execution");
    }

    /** Test command implementation. */
    record TestDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient, Condition condition)
        implements DomainCommand.Update<UUID, Instant> {
      TestDomainCommand(Condition condition) {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance(), condition);
      }

      TestDomainCommand(DomainClient domainClient) {
        this(
            UUID.randomUUID(),
            Instant.now(),
            domainClient,
            TEST_TABLE.CREATED_AT.ge(LocalDateTime.now().minusDays(1)));
      }
    }

    /** Test handler implementation. */
    static class TestHandler
        extends DomainCommandHandler.Update<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onSuccessInvoked;

      TestHandler(Class<TestDomainCommand> commandClass) {
        super(commandClass);
        this.onSuccessInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, Optional<TestTableRecord> testTableRecord) {
        onSuccessInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected TestTableRecord updateRecordValues(
          TestDomainCommand command, TestTableRecord blankRecord) throws Exception {
        onSuccessInvoked.set(false); // Because run invoked before, we can reset the value here
        blankRecord.setCreatedAt(LocalDateTime.now());
        return blankRecord;
      }
    }

    /** Test handler implementation which tries to invoke DSL operation on the record. */
    static class TestDatabaseReachingHandler
        extends DomainCommandHandler.Update<TestDomainCommand, TestTableRecord> {
      TestDatabaseReachingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected TestTableRecord updateRecordValues(
          TestDomainCommand command, TestTableRecord blankRecord) throws Exception {
        blankRecord.setCreatedAt(LocalDateTime.now());
        blankRecord.store(); // Imitating user's action to store the detached record
        return blankRecord;
      }
    }

    /** Test handler implementation which tries to detach DSL from the record. */
    static class TestDatabaseCheatingHandler
        extends DomainCommandHandler.Update<TestDomainCommand, TestTableRecord> {
      TestDatabaseCheatingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected TestTableRecord updateRecordValues(
          TestDomainCommand command, TestTableRecord blankRecord) throws Exception {
        blankRecord.setCreatedAt(LocalDateTime.now());
        blankRecord.detach();
        return blankRecord;
      }
    }

    /**
     * Test handler implementation where {@link
     * DomainCommandHandler#internalRunContract(DomainCommand, DSLContext, DSLContext, Table,
     * Condition, DomainNotificationProducer)} returns {@code null}.
     */
    static class TestNullRunHandler
        extends DomainCommandHandler.Update<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onFailureInvoked;

      TestNullRunHandler() {
        super(TestDomainCommand.class);
        this.onFailureInvoked = new AtomicBoolean(false);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        onFailureInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected TestTableRecord updateRecordValues(
          TestDomainCommand command, TestTableRecord blankRecord) throws Exception {
        onFailureInvoked.set(false); // Because run invoked before, we can reset the value here
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onSuccess(Object, Object)} returns
     * {@code null}.
     */
    static class TestNullSuccessNotificationHandler
        extends DomainCommandHandler.Update<TestDomainCommand, TestTableRecord> {
      TestNullSuccessNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, Optional<TestTableRecord> testTableRecord) {
        return null;
      }

      @Override
      protected TestTableRecord updateRecordValues(
          TestDomainCommand command, TestTableRecord blankRecord) {
        blankRecord.setCreatedAt(LocalDateTime.now());
        return blankRecord;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onFailure(Object, Throwable)} returns
     * {@code null}.
     */
    static class TestNullFailureNotificationHandler
        extends DomainCommandHandler.Update<TestDomainCommand, TestTableRecord> {
      TestNullFailureNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        return null;
      }

      @Override
      protected TestTableRecord updateRecordValues(
          TestDomainCommand command, TestTableRecord blankRecord) {
        return null; // To trigger error
      }
    }
  }

  @Nested
  class Delete {
    static final TestHandler HANDLER = new TestHandler(TestDomainCommand.class);

    LocalDateTime testOriginalValue;
    TestTableRecord testRecord;
    Condition testCondition;
    TestDomainCommand testCommand;

    @BeforeEach
    void setUp() {
      testOriginalValue = LocalDateTime.now();

      testRecord = DSL_CONTEXT.newRecord(TEST_TABLE);
      testRecord.setId(UUID.randomUUID());
      testRecord.setCreatedAt(testOriginalValue);
      DSL_CONTEXT.batchInsert(testRecord).execute();

      testCondition = TEST_TABLE.ID.eq(testRecord.getId());

      testCommand = new TestDomainCommand(testCondition);
    }

    @Test
    void when_command_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(null));
    }

    @Test
    void when_command_class_is_present_it_must_be_not_null() {
      final var handler = assertDoesNotThrow(() -> new TestHandler(TestDomainCommand.class));
      assertNotNull(handler.getCommandClass());
    }

    @Test
    void when_command_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  null,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_command_client_is_null_illegal_state_exception_is_thrown() {
      final var command = new TestDomainCommand((DomainClient) null);

      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_client_is_not_authorized_unauthorized_exception_must_be_thrown() {
      final var command = new TestDomainCommand(AnotherDomainClient.getInstance());

      assertThrows(
          UnauthorizedException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_write_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  null,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_only_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  null,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_table_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  null,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_condition_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  testCommand, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, null, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_notification_producer_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, testCondition, null));
    }

    @Test
    void when_run_failed_original_exception_is_thrown() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          NullPointerException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_success_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullSuccessNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));

      final var databaseState = DSL_CONTEXT.selectFrom(TEST_TABLE).where(testCondition).fetchOne();
      assertNotNull(
          databaseState,
          "Despite success, failure to deliver notification must revert DELETE operation within transaction");
    }

    @Test
    void when_optional_failure_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullFailureNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_all_parameters_were_passed_the_output_must_not_be_null() {
      assertNotNull(
          HANDLER.runInContext(
              testCommand,
              DSL_CONTEXT,
              DSL_CONTEXT,
              TEST_TABLE,
              testCondition,
              NOTIFICATION_PRODUCER));
    }

    @Test
    void when_no_failures_occurred_the_on_success_method_must_be_triggered() {
      assertDoesNotThrow(
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
      assertTrue(HANDLER.onSuccessInvoked.get(), "Expected to invoke onSuccess");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_failure_occurred_the_on_failure_method_must_be_triggered() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          Exception.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
      assertTrue(handler.onFailureInvoked.get(), "Expected to invoke onFailure");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_was_not_overridden_deletion_should_happen() {
      final var handler = new TestDefaultHandler();

      final var deletedRecord =
          assertDoesNotThrow(
              () ->
                  handler.runInContext(
                      testCommand,
                      DSL_CONTEXT,
                      DSL_CONTEXT,
                      TEST_TABLE,
                      testCondition,
                      NOTIFICATION_PRODUCER));

      assertTrue(deletedRecord.isPresent());
      assertEquals(
          0,
          DSL_CONTEXT.selectCount().from(TEST_TABLE).where(testCondition).fetchOne(0, int.class));
    }

    @Test
    void when_user_attempts_to_trigger_database_operation_in_run_exception_is_thrown() {
      final var handler = new TestDatabaseReachingHandler();

      assertThrows(
          DetachedException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));

      final var cheatingHandler = new TestDatabaseCheatingHandler();

      final var deletedRecord =
          assertDoesNotThrow(
              () ->
                  cheatingHandler.runInContext(
                      testCommand,
                      DSL_CONTEXT,
                      DSL_CONTEXT,
                      TEST_TABLE,
                      testCondition,
                      NOTIFICATION_PRODUCER));

      assertTrue(deletedRecord.isPresent(), "We should send deleted record back to consumer");
      final var testTableRecord = deletedRecord.get();
      assertThrows(
          DetachedException.class,
          testTableRecord::refresh,
          "Record must be detached from DSL after execution");
    }

    /** Test command implementation. */
    record TestDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient, Condition condition)
        implements DomainCommand.Delete<UUID, Instant> {
      TestDomainCommand(Condition condition) {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance(), condition);
      }

      TestDomainCommand(DomainClient domainClient) {
        this(
            UUID.randomUUID(),
            Instant.now(),
            domainClient,
            TEST_TABLE.CREATED_AT.ge(LocalDateTime.now().minusDays(1)));
      }
    }

    /** Test handler implementation. */
    static class TestHandler
        extends DomainCommandHandler.Delete<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onSuccessInvoked;

      TestHandler(Class<TestDomainCommand> commandClass) {
        super(commandClass);
        this.onSuccessInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, Optional<TestTableRecord> testTableRecord) {
        onSuccessInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected void beforeDelete(TestDomainCommand command, TestTableRecord blankRecord) {
        onSuccessInvoked.set(false); // Because run invoked before, we can reset the value here
      }
    }

    /** Test handler implementation which tries to invoke DSL operation on the record. */
    static class TestDefaultHandler
        extends DomainCommandHandler.Delete<TestDomainCommand, TestTableRecord> {
      TestDefaultHandler() {
        super(TestDomainCommand.class);
      }
    }

    /** Test handler implementation which tries to invoke DSL operation on the record. */
    static class TestDatabaseReachingHandler
        extends DomainCommandHandler.Delete<TestDomainCommand, TestTableRecord> {
      TestDatabaseReachingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected void beforeDelete(TestDomainCommand command, TestTableRecord blankRecord) {
        blankRecord.delete();
      }
    }

    /** Test handler implementation which tries to attach DSL back to the record. */
    static class TestDatabaseCheatingHandler
        extends DomainCommandHandler.Delete<TestDomainCommand, TestTableRecord> {
      TestDatabaseCheatingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected void beforeDelete(TestDomainCommand command, TestTableRecord blankRecord) {
        blankRecord.attach(DSL_CONTEXT.configuration());
      }
    }

    /**
     * Test handler implementation where {@link
     * DomainCommandHandler#internalRunContract(DomainCommand, DSLContext, DSLContext, Table,
     * Condition, DomainNotificationProducer)} returns {@code null}.
     */
    static class TestNullRunHandler
        extends DomainCommandHandler.Delete<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onFailureInvoked;

      TestNullRunHandler() {
        super(TestDomainCommand.class);
        this.onFailureInvoked = new AtomicBoolean(false);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        onFailureInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected void beforeDelete(TestDomainCommand command, TestTableRecord blankRecord) {
        onFailureInvoked.set(false); // Because run invoked before, we can reset the value here
        throw new NullPointerException();
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onSuccess(Object, Object)} returns
     * {@code null}.
     */
    static class TestNullSuccessNotificationHandler
        extends DomainCommandHandler.Delete<TestDomainCommand, TestTableRecord> {
      TestNullSuccessNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, Optional<TestTableRecord> testTableRecord) {
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onFailure(Object, Throwable)} returns
     * {@code null}.
     */
    static class TestNullFailureNotificationHandler
        extends DomainCommandHandler.Delete<TestDomainCommand, TestTableRecord> {
      TestNullFailureNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        return null;
      }

      @Override
      protected void beforeDelete(TestDomainCommand command, TestTableRecord blankRecord) {
        throw new NullPointerException(); // To trigger error
      }
    }
  }

  @Nested
  class BatchCreate {
    static final TestHandler HANDLER = new TestHandler(TestDomainCommand.class);
    static final TestDomainCommand COMMAND = new TestDomainCommand();

    @Test
    void when_command_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(null));
    }

    @Test
    void when_command_class_is_present_it_must_be_not_null() {
      final var handler = assertDoesNotThrow(() -> new TestHandler(TestDomainCommand.class));
      assertNotNull(handler.getCommandClass());
    }

    @Test
    void when_command_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  null, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_command_client_is_null_illegal_state_exception_is_thrown() {
      final var command = new TestDomainCommand(null);

      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_client_is_not_authorized_unauthorized_exception_must_be_thrown() {
      final var command = new TestDomainCommand(AnotherDomainClient.getInstance());

      assertThrows(
          UnauthorizedException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_write_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, null, DSL_CONTEXT, TEST_TABLE, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_only_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, DSL_CONTEXT, null, TEST_TABLE, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_table_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, DSL_CONTEXT, DSL_CONTEXT, null, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_condition_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, null, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_notification_producer_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  COMMAND, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, NO_CONDITION, null));
    }

    @Test
    void when_run_result_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_success_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullSuccessNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));

      assertEquals(
          0,
          DSL_CONTEXT.selectCount().from(TEST_TABLE).fetchOne(0, int.class),
          "Despite success, failure to deliver notification must revert INSERT operation within transaction");
    }

    @Test
    void when_optional_failure_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullFailureNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_all_parameters_were_passed_the_output_must_not_be_null() {
      assertNotNull(
          HANDLER.runInContext(
              COMMAND, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, NO_CONDITION, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_no_failures_occurred_the_on_success_method_must_be_triggered() {
      assertDoesNotThrow(
          () ->
              HANDLER.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
      assertTrue(HANDLER.onSuccessInvoked.get(), "Expected to invoke onSuccess");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_failure_occurred_the_on_failure_method_must_be_triggered() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          Exception.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));
      assertTrue(handler.onFailureInvoked.get(), "Expected to invoke onFailure");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_user_attempts_to_trigger_database_operation_in_run_exception_is_thrown() {
      final var handler = new TestDatabaseReachingHandler();

      assertThrows(
          DetachedException.class,
          () ->
              handler.runInContext(
                  COMMAND,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  NO_CONDITION,
                  NOTIFICATION_PRODUCER));

      final var cheatingHandler = new TestDatabaseCheatingHandler();

      final var savedRecords =
          assertDoesNotThrow(
              () ->
                  cheatingHandler.runInContext(
                      COMMAND,
                      DSL_CONTEXT,
                      DSL_CONTEXT,
                      TEST_TABLE,
                      NO_CONDITION,
                      NOTIFICATION_PRODUCER));

      assertEquals(1, savedRecords.size());
      assertDoesNotThrow(
          () -> savedRecords.get(0).refresh(), "Record must be reattached to DSL after execution");
    }

    /** Test command implementation. */
    record TestDomainCommand(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainCommand.BatchCreate<UUID, Instant> {
      TestDomainCommand(DomainClient domainClient) {
        this(UUID.randomUUID(), Instant.now(), domainClient);
      }

      TestDomainCommand() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test handler implementation. */
    static class TestHandler
        extends DomainCommandHandler.BatchCreate<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onSuccessInvoked;

      TestHandler(Class<TestDomainCommand> commandClass) {
        super(commandClass);
        this.onSuccessInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, List<TestTableRecord> testTableRecords) {
        onSuccessInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected Stream<TestTableRecord> fillBlankRecords(
          TestDomainCommand command, Supplier<TestTableRecord> blankRecordSupplier) {
        onSuccessInvoked.set(false); // Because run invoked before, we can reset the value here
        TestTableRecord blankRecord = blankRecordSupplier.get();
        blankRecord.setId(UUID.randomUUID());
        blankRecord.setCreatedAt(LocalDateTime.now());
        return Stream.of(blankRecord);
      }
    }

    /** Test handler implementation which tries to invoke DSL operation on the record. */
    static class TestDatabaseReachingHandler
        extends DomainCommandHandler.BatchCreate<TestDomainCommand, TestTableRecord> {
      TestDatabaseReachingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Stream<TestTableRecord> fillBlankRecords(
          TestDomainCommand command, Supplier<TestTableRecord> blankRecordSupplier) {
        TestTableRecord blankRecord = blankRecordSupplier.get();
        blankRecord.setId(UUID.randomUUID());
        blankRecord.setCreatedAt(LocalDateTime.now());
        blankRecord.store();
        return Stream.of(blankRecord);
      }
    }

    /** Test handler implementation which tries to detach DSL from the record. */
    static class TestDatabaseCheatingHandler
        extends DomainCommandHandler.BatchCreate<TestDomainCommand, TestTableRecord> {
      TestDatabaseCheatingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Stream<TestTableRecord> fillBlankRecords(
          TestDomainCommand command, Supplier<TestTableRecord> blankRecordSupplier) {
        TestTableRecord blankRecord = blankRecordSupplier.get();
        blankRecord.setId(UUID.randomUUID());
        blankRecord.setCreatedAt(LocalDateTime.now());
        blankRecord.detach();
        return Stream.of(blankRecord);
      }
    }

    /**
     * Test handler implementation where {@link
     * DomainCommandHandler#internalRunContract(DomainCommand, DSLContext, DSLContext, Table,
     * Condition, DomainNotificationProducer)} returns {@code null}.
     */
    static class TestNullRunHandler
        extends DomainCommandHandler.BatchCreate<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onFailureInvoked;

      TestNullRunHandler() {
        super(TestDomainCommand.class);
        this.onFailureInvoked = new AtomicBoolean(false);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        onFailureInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected Stream<TestTableRecord> fillBlankRecords(
          TestDomainCommand command, Supplier<TestTableRecord> blankRecordSupplier) {
        onFailureInvoked.set(false); // Because run invoked before, we can reset the value here
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onSuccess(Object, Object)} returns
     * {@code null}.
     */
    static class TestNullSuccessNotificationHandler
        extends DomainCommandHandler.BatchCreate<TestDomainCommand, TestTableRecord> {
      TestNullSuccessNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, List<TestTableRecord> testTableRecords) {
        return null;
      }

      @Override
      protected Stream<TestTableRecord> fillBlankRecords(
          TestDomainCommand command, Supplier<TestTableRecord> blankRecordSupplier) {
        TestTableRecord blankRecord = blankRecordSupplier.get();
        blankRecord.setId(UUID.randomUUID());
        blankRecord.setCreatedAt(LocalDateTime.now());
        return Stream.of(blankRecord);
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onFailure(Object, Throwable)} returns
     * {@code null}.
     */
    static class TestNullFailureNotificationHandler
        extends DomainCommandHandler.BatchCreate<TestDomainCommand, TestTableRecord> {
      TestNullFailureNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        return null;
      }

      @Override
      protected Stream<TestTableRecord> fillBlankRecords(
          TestDomainCommand command, Supplier<TestTableRecord> blankRecordSupplier) {
        return null; // To trigger error
      }
    }
  }

  @Nested
  class BatchUpdate {
    static final TestHandler HANDLER = new TestHandler(TestDomainCommand.class);

    LocalDateTime testOriginalValue;
    TestTableRecord testRecord;
    Condition testCondition;
    TestDomainCommand testCommand;

    @BeforeEach
    void setUp() {
      testOriginalValue = LocalDateTime.now();

      testRecord = DSL_CONTEXT.newRecord(TEST_TABLE);
      testRecord.setId(UUID.randomUUID());
      testRecord.setCreatedAt(testOriginalValue);
      DSL_CONTEXT.batchInsert(testRecord).execute();

      testCondition = TEST_TABLE.ID.eq(testRecord.getId());

      testCommand = new TestDomainCommand(testCondition);
    }

    @Test
    void when_command_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(null));
    }

    @Test
    void when_command_class_is_present_it_must_be_not_null() {
      final var handler = assertDoesNotThrow(() -> new TestHandler(TestDomainCommand.class));
      assertNotNull(handler.getCommandClass());
    }

    @Test
    void when_command_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  null,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_command_client_is_null_illegal_state_exception_is_thrown() {
      final var command = new TestDomainCommand((DomainClient) null);

      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_client_is_not_authorized_unauthorized_exception_must_be_thrown() {
      final var command = new TestDomainCommand(AnotherDomainClient.getInstance());

      assertThrows(
          UnauthorizedException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_write_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  null,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_only_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  null,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_table_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  null,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_condition_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  testCommand, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, null, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_notification_producer_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, testCondition, null));
    }

    @Test
    void when_run_result_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_success_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullSuccessNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));

      final var databaseState = DSL_CONTEXT.selectFrom(TEST_TABLE).where(testCondition).fetchOne();
      assertNotNull(databaseState);
      assertEquals(
          testOriginalValue.getYear(),
          databaseState.getCreatedAt().getYear(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getMonth(),
          databaseState.getCreatedAt().getMonth(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getDayOfMonth(),
          databaseState.getCreatedAt().getDayOfMonth(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getHour(),
          databaseState.getCreatedAt().getHour(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getMinute(),
          databaseState.getCreatedAt().getMinute(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
      assertEquals(
          testOriginalValue.getSecond(),
          databaseState.getCreatedAt().getSecond(),
          "Despite success, failure to deliver notification must revert UPDATE operation within transaction");
    }

    @Test
    void when_optional_failure_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullFailureNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_all_parameters_were_passed_the_output_must_not_be_null() {
      assertNotNull(
          HANDLER.runInContext(
              testCommand,
              DSL_CONTEXT,
              DSL_CONTEXT,
              TEST_TABLE,
              testCondition,
              NOTIFICATION_PRODUCER));
    }

    @Test
    void when_no_failures_occurred_the_on_success_method_must_be_triggered() {
      assertDoesNotThrow(
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
      assertTrue(HANDLER.onSuccessInvoked.get(), "Expected to invoke onSuccess");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_failure_occurred_the_on_failure_method_must_be_triggered() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          Exception.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
      assertTrue(handler.onFailureInvoked.get(), "Expected to invoke onFailure");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_user_attempts_to_trigger_database_operation_in_run_exception_is_thrown() {
      final var handler = new TestDatabaseReachingHandler();

      assertThrows(
          DetachedException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));

      final var cheatingHandler = new TestDatabaseCheatingHandler();

      final var updatedRecords =
          assertDoesNotThrow(
              () ->
                  cheatingHandler.runInContext(
                      testCommand,
                      DSL_CONTEXT,
                      DSL_CONTEXT,
                      TEST_TABLE,
                      testCondition,
                      NOTIFICATION_PRODUCER));

      assertEquals(1, updatedRecords.size());
      assertDoesNotThrow(
          () -> updatedRecords.get(0).refresh(),
          "Record must be reattached to DSL after execution");
    }

    /** Test command implementation. */
    record TestDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient, Condition condition)
        implements DomainCommand.BatchUpdate<UUID, Instant> {
      TestDomainCommand(Condition condition) {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance(), condition);
      }

      TestDomainCommand(DomainClient domainClient) {
        this(
            UUID.randomUUID(),
            Instant.now(),
            domainClient,
            TEST_TABLE.CREATED_AT.ge(LocalDateTime.now().minusDays(1)));
      }
    }

    /** Test handler implementation. */
    static class TestHandler
        extends DomainCommandHandler.BatchUpdate<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onSuccessInvoked;

      TestHandler(Class<TestDomainCommand> commandClass) {
        super(commandClass);
        this.onSuccessInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, List<TestTableRecord> testTableRecords) {
        onSuccessInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected Stream<TestTableRecord> updateRecordValues(
          TestDomainCommand command, Stream<TestTableRecord> oldRecords) {
        onSuccessInvoked.set(false); // Because run invoked before, we can reset the value here
        return oldRecords.map(
            testTableRecord -> {
              testTableRecord.setCreatedAt(LocalDateTime.now());
              return testTableRecord;
            });
      }
    }

    /** Test handler implementation which tries to invoke DSL operation on the record. */
    static class TestDatabaseReachingHandler
        extends DomainCommandHandler.BatchUpdate<TestDomainCommand, TestTableRecord> {
      TestDatabaseReachingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Stream<TestTableRecord> updateRecordValues(
          TestDomainCommand command, Stream<TestTableRecord> oldRecords) {
        return oldRecords.map(
            testTableRecord -> {
              testTableRecord.setCreatedAt(LocalDateTime.now());
              testTableRecord.store();
              return testTableRecord;
            });
      }
    }

    /** Test handler implementation which tries to detach DSL from the record. */
    static class TestDatabaseCheatingHandler
        extends DomainCommandHandler.BatchUpdate<TestDomainCommand, TestTableRecord> {
      TestDatabaseCheatingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Stream<TestTableRecord> updateRecordValues(
          TestDomainCommand command, Stream<TestTableRecord> oldRecords) {
        return oldRecords.map(
            testTableRecord -> {
              testTableRecord.setCreatedAt(LocalDateTime.now());
              testTableRecord.detach();
              return testTableRecord;
            });
      }
    }

    /**
     * Test handler implementation where {@link
     * DomainCommandHandler#internalRunContract(DomainCommand, DSLContext, DSLContext, Table,
     * Condition, DomainNotificationProducer)} returns {@code null}.
     */
    static class TestNullRunHandler
        extends DomainCommandHandler.BatchUpdate<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onFailureInvoked;

      TestNullRunHandler() {
        super(TestDomainCommand.class);
        this.onFailureInvoked = new AtomicBoolean(false);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        onFailureInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected Stream<TestTableRecord> updateRecordValues(
          TestDomainCommand command, Stream<TestTableRecord> oldRecords) {
        onFailureInvoked.set(false); // Because run invoked before, we can reset the value here
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onSuccess(Object, Object)} returns
     * {@code null}.
     */
    static class TestNullSuccessNotificationHandler
        extends DomainCommandHandler.BatchUpdate<TestDomainCommand, TestTableRecord> {
      TestNullSuccessNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, List<TestTableRecord> testTableRecords) {
        return null;
      }

      @Override
      protected Stream<TestTableRecord> updateRecordValues(
          TestDomainCommand command, Stream<TestTableRecord> oldRecords) {
        return oldRecords.map(
            oldRecord -> {
              oldRecord.setCreatedAt(LocalDateTime.now());
              return oldRecord;
            });
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onFailure(Object, Throwable)} returns
     * {@code null}.
     */
    static class TestNullFailureNotificationHandler
        extends DomainCommandHandler.BatchUpdate<TestDomainCommand, TestTableRecord> {
      TestNullFailureNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        return null;
      }

      @Override
      protected Stream<TestTableRecord> updateRecordValues(
          TestDomainCommand command, Stream<TestTableRecord> oldRecords) {
        return null; // To trigger error
      }
    }
  }

  @Nested
  class BatchDelete {
    static final TestHandler HANDLER = new TestHandler(TestDomainCommand.class);

    LocalDateTime testOriginalValue;
    TestTableRecord testRecord;
    Condition testCondition;
    TestDomainCommand testCommand;

    @BeforeEach
    void setUp() {
      testOriginalValue = LocalDateTime.now();

      testRecord = DSL_CONTEXT.newRecord(TEST_TABLE);
      testRecord.setId(UUID.randomUUID());
      testRecord.setCreatedAt(testOriginalValue);
      DSL_CONTEXT.batchInsert(testRecord).execute();

      testCondition = TEST_TABLE.ID.eq(testRecord.getId());

      testCommand = new TestDomainCommand(testCondition);
    }

    @Test
    void when_command_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(null));
    }

    @Test
    void when_command_class_is_present_it_must_be_not_null() {
      final var handler = assertDoesNotThrow(() -> new TestHandler(TestDomainCommand.class));
      assertNotNull(handler.getCommandClass());
    }

    @Test
    void when_command_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  null,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_command_client_is_null_illegal_state_exception_is_thrown() {
      final var command = new TestDomainCommand((DomainClient) null);

      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_client_is_not_authorized_unauthorized_exception_must_be_thrown() {
      final var command = new TestDomainCommand(AnotherDomainClient.getInstance());

      assertThrows(
          UnauthorizedException.class,
          () ->
              HANDLER.runInContext(
                  command,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_write_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  null,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_only_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  null,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_table_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  null,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_condition_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              HANDLER.runInContext(
                  testCommand, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, null, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_notification_producer_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () ->
              HANDLER.runInContext(
                  testCommand, DSL_CONTEXT, DSL_CONTEXT, TEST_TABLE, testCondition, null));
    }

    @Test
    void when_run_result_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_success_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullSuccessNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));

      final var databaseState = DSL_CONTEXT.selectFrom(TEST_TABLE).where(testCondition).fetchOne();
      assertNotNull(
          databaseState,
          "Despite success, failure to deliver notification must revert DELETE operation within transaction");
    }

    @Test
    void when_optional_failure_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullFailureNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
    }

    @Test
    void when_all_parameters_were_passed_the_output_must_not_be_null() {
      assertNotNull(
          HANDLER.runInContext(
              testCommand,
              DSL_CONTEXT,
              DSL_CONTEXT,
              TEST_TABLE,
              testCondition,
              NOTIFICATION_PRODUCER));
    }

    @Test
    void when_no_failures_occurred_the_on_success_method_must_be_triggered() {
      assertDoesNotThrow(
          () ->
              HANDLER.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
      assertTrue(HANDLER.onSuccessInvoked.get(), "Expected to invoke onSuccess");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_failure_occurred_the_on_failure_method_must_be_triggered() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          Exception.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));
      assertTrue(handler.onFailureInvoked.get(), "Expected to invoke onFailure");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_was_not_overridden_deletion_should_happen() {
      final var handler = new TestDefaultHandler();

      final var deletedRecords =
          assertDoesNotThrow(
              () ->
                  handler.runInContext(
                      testCommand,
                      DSL_CONTEXT,
                      DSL_CONTEXT,
                      TEST_TABLE,
                      testCondition,
                      NOTIFICATION_PRODUCER));

      assertEquals(1, deletedRecords.size());
      assertEquals(
          0,
          DSL_CONTEXT.selectCount().from(TEST_TABLE).where(testCondition).fetchOne(0, int.class));
    }

    @Test
    void when_user_attempts_to_trigger_database_operation_in_run_exception_is_thrown() {
      final var handler = new TestDatabaseReachingHandler();

      assertThrows(
          DetachedException.class,
          () ->
              handler.runInContext(
                  testCommand,
                  DSL_CONTEXT,
                  DSL_CONTEXT,
                  TEST_TABLE,
                  testCondition,
                  NOTIFICATION_PRODUCER));

      final var cheatingHandler = new TestDatabaseCheatingHandler();

      final var deletedRecords =
          assertDoesNotThrow(
              () ->
                  cheatingHandler.runInContext(
                      testCommand,
                      DSL_CONTEXT,
                      DSL_CONTEXT,
                      TEST_TABLE,
                      testCondition,
                      NOTIFICATION_PRODUCER));

      assertEquals(1, deletedRecords.size());
      final var deletedRecord = deletedRecords.get(0);
      assertThrows(
          DetachedException.class,
          deletedRecord::refresh,
          "Record must be detached from DSL after execution");
    }

    /** Test command implementation. */
    record TestDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient, Condition condition)
        implements DomainCommand.BatchDelete<UUID, Instant> {
      TestDomainCommand(Condition condition) {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance(), condition);
      }

      TestDomainCommand(DomainClient domainClient) {
        this(
            UUID.randomUUID(),
            Instant.now(),
            domainClient,
            TEST_TABLE.CREATED_AT.ge(LocalDateTime.now().minusDays(1)));
      }
    }

    /** Test handler implementation. */
    static class TestHandler
        extends DomainCommandHandler.BatchDelete<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onSuccessInvoked;

      TestHandler(Class<TestDomainCommand> commandClass) {
        super(commandClass);
        this.onSuccessInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, List<TestTableRecord> testTableRecords) {
        onSuccessInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected Stream<TestTableRecord> beforeDelete(
          TestDomainCommand command, Stream<TestTableRecord> dbRecords) {
        onSuccessInvoked.set(false); // Because run invoked before, we can reset the value here
        return dbRecords;
      }
    }

    /** Test handler implementation which does not override the default behavior. */
    static class TestDefaultHandler
        extends DomainCommandHandler.BatchDelete<TestDomainCommand, TestTableRecord> {
      TestDefaultHandler() {
        super(TestDomainCommand.class);
      }
    }

    /** Test handler implementation which tries to invoke DSL operation on the record. */
    static class TestDatabaseReachingHandler
        extends DomainCommandHandler.BatchDelete<TestDomainCommand, TestTableRecord> {
      TestDatabaseReachingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Stream<TestTableRecord> beforeDelete(
          TestDomainCommand command, Stream<TestTableRecord> dbRecords) {
        return dbRecords.map(
            dbRecord -> {
              dbRecord.delete();
              return dbRecord;
            });
      }
    }

    /** Test handler implementation which tries to attach DSL back to the record. */
    static class TestDatabaseCheatingHandler
        extends DomainCommandHandler.BatchDelete<TestDomainCommand, TestTableRecord> {
      TestDatabaseCheatingHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Stream<TestTableRecord> beforeDelete(
          TestDomainCommand command, Stream<TestTableRecord> dbRecords) {
        return dbRecords.map(
            dbRecord -> {
              dbRecord.attach(DSL_CONTEXT.configuration());
              return dbRecord;
            });
      }
    }

    /**
     * Test handler implementation where {@link
     * DomainCommandHandler#internalRunContract(DomainCommand, DSLContext, DSLContext, Table,
     * Condition, DomainNotificationProducer)} returns {@code null}.
     */
    static class TestNullRunHandler
        extends DomainCommandHandler.BatchDelete<TestDomainCommand, TestTableRecord> {
      final AtomicBoolean onFailureInvoked;

      TestNullRunHandler() {
        super(TestDomainCommand.class);
        this.onFailureInvoked = new AtomicBoolean(false);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        onFailureInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected Stream<TestTableRecord> beforeDelete(
          TestDomainCommand command, Stream<TestTableRecord> dbRecords) {
        onFailureInvoked.set(false); // Because run invoked before, we can reset the value here
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onSuccess(Object, Object)} returns
     * {@code null}.
     */
    static class TestNullSuccessNotificationHandler
        extends DomainCommandHandler.BatchDelete<TestDomainCommand, TestTableRecord> {
      TestNullSuccessNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestDomainCommand testCommand, List<TestTableRecord> testTableRecords) {
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onFailure(Object, Throwable)} returns
     * {@code null}.
     */
    static class TestNullFailureNotificationHandler
        extends DomainCommandHandler.BatchDelete<TestDomainCommand, TestTableRecord> {
      TestNullFailureNotificationHandler() {
        super(TestDomainCommand.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(
          TestDomainCommand testCommand, Throwable cause) {
        return null;
      }

      @Override
      protected Stream<TestTableRecord> beforeDelete(
          TestDomainCommand command, Stream<TestTableRecord> dbRecords) {
        return null; // To trigger error
      }
    }
  }
}
