package io.github.suppierk.ddd.cqrs;

import static io.github.suppierk.test.example.Tables.TEST_TABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DomainViewHandlerTest {
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
                      DomainViewHandlerTest.class
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

    final var testRecord = DSL_CONTEXT.newRecord(TEST_TABLE);
    testRecord.setId(UUID.randomUUID());
    testRecord.setCreatedAt(LocalDateTime.now());
    DSL_CONTEXT.batchInsert(testRecord).execute();
  }

  @Nested
  class One {
    static final TestHandler HANDLER = new TestHandler(TestView.class, Object.class);
    static final TestView VIEW = new TestView();

    @Test
    void when_view_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(null, Object.class));
    }

    @Test
    void when_view_output_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(TestView.class, null));
    }

    @Test
    void when_view_and_view_output_classes_are_present_they_must_be_not_null() {
      final var handler = assertDoesNotThrow(() -> new TestHandler(TestView.class, Object.class));
      assertNotNull(handler.getViewClass());
      assertNotNull(handler.getViewOutputClass());
    }

    @Test
    void when_view_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () -> HANDLER.runInContext(null, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_view_client_is_null_illegal_state_exception_is_thrown() {
      final var view = new TestView(null);

      assertThrows(
          IllegalStateException.class,
          () -> HANDLER.runInContext(view, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_client_is_not_authorized_unauthorized_exception_must_be_thrown() {
      final var view = new TestView(AnotherDomainClient.getInstance());

      assertThrows(
          UnauthorizedException.class,
          () -> HANDLER.runInContext(view, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_write_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () -> HANDLER.runInContext(VIEW, null, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_only_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () -> HANDLER.runInContext(VIEW, DSL_CONTEXT, null, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_notification_producer_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () -> HANDLER.runInContext(VIEW, DSL_CONTEXT, DSL_CONTEXT, null));
    }

    @Test
    void when_run_result_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          IllegalStateException.class,
          () -> handler.runInContext(VIEW, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_success_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullSuccessNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () -> handler.runInContext(VIEW, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_failure_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullFailureNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () -> handler.runInContext(VIEW, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_all_parameters_were_passed_the_output_must_not_be_null() {
      assertNotNull(HANDLER.runInContext(VIEW, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_no_failures_occurred_the_on_success_method_must_be_triggered() {
      final var result =
          assertDoesNotThrow(
              () -> HANDLER.runInContext(VIEW, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
      assertTrue(result.isPresent());
      assertTrue(HANDLER.onSuccessInvoked.get(), "Expected to invoke onSuccess");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_failure_occurred_the_on_failure_method_must_be_triggered() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          Exception.class,
          () -> handler.runInContext(VIEW, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
      assertTrue(handler.onFailureInvoked.get(), "Expected to invoke onFailure");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    /** Test view implementation. */
    record TestView(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainView.One<UUID, Instant> {
      TestView(DomainClient domainClient) {
        this(UUID.randomUUID(), Instant.now(), domainClient);
      }

      TestView() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test handler implementation. */
    static class TestHandler extends DomainViewHandler.One<TestView, Object> {
      final AtomicBoolean onSuccessInvoked;

      TestHandler(Class<TestView> viewClass, Class<Object> viewOutputClass) {
        super(viewClass, viewOutputClass);
        this.onSuccessInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestView testView, Optional<Object> o) {
        onSuccessInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected Optional<Object> run(TestView view, DSLContext dsl) {
        onSuccessInvoked.set(false); // Because run invoked before, we can reset the value here
        return dsl.selectFrom(TEST_TABLE).limit(1).fetchOptional().map(this::convert);
      }

      @Override
      protected Object convert(Record databaseRecord) {
        return databaseRecord;
      }
    }

    /**
     * Test handler implementation where {@link DomainViewHandler#run(DomainView, DSLContext)}
     * returns {@code null}.
     */
    static class TestNullRunHandler extends DomainViewHandler.One<TestView, Object> {
      final AtomicBoolean onFailureInvoked;

      TestNullRunHandler() {
        super(TestView.class, Object.class);
        this.onFailureInvoked = new AtomicBoolean(false);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(TestView testView, Throwable cause) {
        onFailureInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected Optional<Object> run(TestView view, DSLContext dsl) {
        onFailureInvoked.set(false); // Because run invoked before, we can reset the value here
        return null;
      }

      @Override
      protected Object convert(Record databaseRecord) {
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onSuccess(Object, Object)} returns
     * {@code null}.
     */
    static class TestNullSuccessNotificationHandler
        extends DomainViewHandler.One<TestView, Object> {
      TestNullSuccessNotificationHandler() {
        super(TestView.class, Object.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestView testView, Optional<Object> emptyRecord) {
        return null;
      }

      @Override
      protected Optional<Object> run(TestView view, DSLContext dsl) {
        return Optional.empty();
      }

      @Override
      protected Object convert(Record databaseRecord) {
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onFailure(Object, Throwable)} returns
     * {@code null}.
     */
    static class TestNullFailureNotificationHandler
        extends DomainViewHandler.One<TestView, Object> {

      TestNullFailureNotificationHandler() {
        super(TestView.class, Object.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(TestView testView, Throwable cause) {
        return null;
      }

      @Override
      protected Optional<Object> run(TestView view, DSLContext dsl) {
        return null; // To trigger error
      }

      @Override
      protected Object convert(Record databaseRecord) {
        return null;
      }
    }
  }

  @Nested
  class Many {
    static final TestHandler HANDLER = new TestHandler(TestView.class, Object.class);
    static final TestView QUERY = new TestView();

    @Test
    void when_view_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(null, Object.class));
    }

    @Test
    void when_view_output_class_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(IllegalArgumentException.class, () -> new TestHandler(TestView.class, null));
    }

    @Test
    void when_view_and_view_output_classes_are_present_they_must_be_not_null() {
      final var handler = assertDoesNotThrow(() -> new TestHandler(TestView.class, Object.class));
      assertNotNull(handler.getViewClass());
      assertNotNull(handler.getViewOutputClass());
    }

    @Test
    void when_view_is_null_illegal_argument_exception_is_thrown() {
      assertThrows(
          IllegalArgumentException.class,
          () -> HANDLER.runInContext(null, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_view_client_is_null_illegal_state_exception_is_thrown() {
      final var view = new TestView(null);

      assertThrows(
          IllegalStateException.class,
          () -> HANDLER.runInContext(view, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_client_is_not_authorized_unauthorized_exception_must_be_thrown() {
      final var view = new TestView(AnotherDomainClient.getInstance());

      assertThrows(
          UnauthorizedException.class,
          () -> HANDLER.runInContext(view, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_write_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () -> HANDLER.runInContext(QUERY, null, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_read_only_dsl_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () -> HANDLER.runInContext(QUERY, DSL_CONTEXT, null, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_notification_producer_is_null_illegal_state_exception_is_thrown() {
      assertThrows(
          IllegalStateException.class,
          () -> HANDLER.runInContext(QUERY, DSL_CONTEXT, DSL_CONTEXT, null));
    }

    @Test
    void when_run_result_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          IllegalStateException.class,
          () -> handler.runInContext(QUERY, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_success_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullSuccessNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () -> handler.runInContext(QUERY, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_optional_failure_notification_is_null_illegal_state_exception_is_thrown() {
      final var handler = new TestNullFailureNotificationHandler();

      assertThrows(
          IllegalStateException.class,
          () -> handler.runInContext(QUERY, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_no_failures_occurred_the_on_success_method_must_be_triggered() {
      final var result =
          assertDoesNotThrow(
              () -> HANDLER.runInContext(QUERY, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
      assertFalse(result.isEmpty());
      assertTrue(HANDLER.onSuccessInvoked.get(), "Expected to invoke onSuccess");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    @Test
    void when_run_failure_occurred_the_on_failure_method_must_be_triggered() {
      final var handler = new TestNullRunHandler();

      assertThrows(
          Exception.class,
          () -> handler.runInContext(QUERY, DSL_CONTEXT, DSL_CONTEXT, NOTIFICATION_PRODUCER));
      assertTrue(handler.onFailureInvoked.get(), "Expected to invoke onFailure");
      assertTrue(
          NOTIFICATION_DELIVERY_REQUESTED.get(), "Expected to invoke Notification Publisher");
    }

    /** Test view implementation. */
    record TestView(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainView.Many<UUID, Instant> {
      TestView(DomainClient domainClient) {
        this(UUID.randomUUID(), Instant.now(), domainClient);
      }

      TestView() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test handler implementation. */
    static class TestHandler extends DomainViewHandler.Many<TestView, Object> {
      final AtomicBoolean onSuccessInvoked;

      TestHandler(Class<TestView> viewClass, Class<Object> viewOutputClass) {
        super(viewClass, viewOutputClass);
        this.onSuccessInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestView testView, List<Object> objectStream) {
        onSuccessInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected List<Object> run(TestView view, DSLContext dsl) {
        onSuccessInvoked.set(false);
        return dsl.selectFrom(TEST_TABLE).limit(1).fetchStream().map(this::convert).toList();
      }

      @Override
      protected Object convert(Record databaseRecord) {
        return databaseRecord;
      }
    }

    /**
     * Test handler implementation where {@link DomainViewHandler#run(DomainView, DSLContext)}
     * returns {@code null}.
     */
    static class TestNullRunHandler extends DomainViewHandler.Many<TestView, Object> {
      final AtomicBoolean onFailureInvoked;

      TestNullRunHandler() {
        super(TestView.class, Object.class);
        this.onFailureInvoked = new AtomicBoolean(false);
      }

      @Override
      protected boolean canBeUsedBy(DomainClient domainClient) {
        return domainClient instanceof AnonymousDomainClient;
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(TestView testView, Throwable cause) {
        onFailureInvoked.set(true);
        return Optional.of(new EmptyDomainNotification());
      }

      @Override
      protected List<Object> run(TestView view, DSLContext dsl) {
        onFailureInvoked.set(false);
        return null;
      }

      @Override
      protected Object convert(Record databaseRecord) {
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onSuccess(Object, Object)} returns
     * {@code null}.
     */
    static class TestNullSuccessNotificationHandler
        extends DomainViewHandler.Many<TestView, Object> {
      TestNullSuccessNotificationHandler() {
        super(TestView.class, Object.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onSuccess(
          TestView testView, List<Object> emptyRecord) {
        return null;
      }

      @Override
      protected List<Object> run(TestView view, DSLContext dsl) {
        return Collections.emptyList();
      }

      @Override
      protected Object convert(Record databaseRecord) {
        return null;
      }
    }

    /**
     * Test handler implementation where {@link DomainHandler#onFailure(Object, Throwable)} returns
     * {@code null}.
     */
    static class TestNullFailureNotificationHandler
        extends DomainViewHandler.Many<TestView, Object> {
      TestNullFailureNotificationHandler() {
        super(TestView.class, Object.class);
      }

      @Override
      protected Optional<DomainNotification<?, ?>> onFailure(TestView testView, Throwable cause) {
        return null;
      }

      @Override
      protected List<Object> run(TestView view, DSLContext dsl) {
        return null; // To trigger error
      }

      @Override
      protected Object convert(Record databaseRecord) {
        return null;
      }
    }
  }
}
