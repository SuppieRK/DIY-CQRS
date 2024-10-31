package io.github.suppierk.ddd.cqrs;

import static io.github.suppierk.test.example.Tables.TEST_TABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.suppierk.ddd.async.DomainNotification;
import io.github.suppierk.ddd.async.DomainNotificationProducer;
import io.github.suppierk.ddd.authorization.AnonymousDomainClient;
import io.github.suppierk.ddd.authorization.DomainClient;
import io.github.suppierk.ddd.jooq.DslContextProvider;
import io.github.suppierk.test.example.tables.records.TestTableRecord;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BoundedContextTest {
  static final AtomicBoolean NOTIFICATION_DELIVERY_REQUESTED;
  static final DomainNotificationProducer NOTIFICATION_PRODUCER;
  static final DSLContext DSL_CONTEXT;
  static final DslContextProvider DSL_CONTEXT_PROVIDER;

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
                      DomainQueryHandlerTest.class
                          .getClassLoader()
                          .getResource("test_table.sql")
                          .toURI())));

      connection.prepareStatement(createTestTableSql).execute();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    DSL_CONTEXT_PROVIDER = DslContextProvider.dslContextIdentity(DSL_CONTEXT);
  }

  @BeforeEach
  void setUp() {
    NOTIFICATION_DELIVERY_REQUESTED.set(false);
    DSL_CONTEXT.truncate(TEST_TABLE).execute();
  }

  @Nested
  class Construction {
    @Test
    void when_any_of_the_constructor_arguments_is_null_throw_illegal_argument_exception() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new TestContext(
                  null, DSL_CONTEXT_PROVIDER, DSL_CONTEXT_PROVIDER, NOTIFICATION_PRODUCER));
      assertThrows(
          IllegalArgumentException.class,
          () -> new TestContext(TEST_TABLE, null, DSL_CONTEXT_PROVIDER, NOTIFICATION_PRODUCER));
      assertThrows(
          IllegalArgumentException.class,
          () -> new TestContext(TEST_TABLE, DSL_CONTEXT_PROVIDER, null, NOTIFICATION_PRODUCER));
      assertThrows(
          IllegalArgumentException.class,
          () -> new TestContext(TEST_TABLE, DSL_CONTEXT_PROVIDER, DSL_CONTEXT_PROVIDER, null));
      assertDoesNotThrow(
          () ->
              new TestContext(
                  TEST_TABLE, DSL_CONTEXT_PROVIDER, DSL_CONTEXT_PROVIDER, NOTIFICATION_PRODUCER));
    }

    @Test
    void when_adding_null_command_handler_illegal_argument_must_be_thrown() {
      final var context = new TestContext();
      assertThrows(IllegalArgumentException.class, () -> context.addDomainCommandHandler(null));
      assertTrue(context.getSupportedDomainCommandClasses().isEmpty());
    }

    @Test
    void when_adding_existing_command_handler_illegal_state_must_be_thrown() {
      final var context = new TestContext();
      final var commandHandler =
          new DomainCommandHandlerTest.Create.TestHandler(
              DomainCommandHandlerTest.Create.TestDomainCommand.class);
      context.addDomainCommandHandler(commandHandler);

      assertTrue(
          context
              .getSupportedDomainCommandClasses()
              .contains(DomainCommandHandlerTest.Create.TestDomainCommand.class));
      assertFalse(context.isAnyWriteLockHeld());

      assertThrows(
          IllegalStateException.class, () -> context.addDomainCommandHandler(commandHandler));
    }

    @Test
    void when_adding_null_query_handler_illegal_argument_must_be_thrown() {
      final var context = new TestContext();
      assertThrows(IllegalArgumentException.class, () -> context.addDomainQueryHandler(null));
      assertTrue(context.getSupportedDomainQueryClasses().isEmpty());
    }

    @Test
    void when_adding_existing_query_handler_illegal_state_must_be_thrown() {
      final var context = new TestContext();
      final var queryHandler =
          new DomainQueryHandlerTest.One.TestHandler(DomainQueryHandlerTest.One.TestQuery.class);
      context.addDomainQueryHandler(queryHandler);

      assertTrue(
          context
              .getSupportedDomainQueryClasses()
              .contains(DomainQueryHandlerTest.One.TestQuery.class));
      assertFalse(context.isAnyWriteLockHeld());

      assertThrows(IllegalStateException.class, () -> context.addDomainQueryHandler(queryHandler));
    }

    @Test
    void when_adding_null_view_handler_illegal_argument_must_be_thrown() {
      final var context = new TestContext();
      assertThrows(IllegalArgumentException.class, () -> context.addDomainViewHandler(null));
      assertTrue(context.getSupportedDomainViewClasses().isEmpty());
    }

    @Test
    void when_adding_existing_view_handler_illegal_state_must_be_thrown() {
      final var context = new TestContext();
      final var viewHandler =
          new DomainViewHandlerTest.One.TestHandler(
              DomainViewHandlerTest.One.TestView.class, Object.class);
      context.addDomainViewHandler(viewHandler);

      assertTrue(
          context
              .getSupportedDomainViewClasses()
              .contains(DomainViewHandlerTest.One.TestView.class));
      assertFalse(context.isAnyWriteLockHeld());

      assertThrows(IllegalStateException.class, () -> context.addDomainViewHandler(viewHandler));
    }

    /** Test context to play around with. */
    static class TestContext extends BoundedContext<TestTableRecord> {
      TestContext() {
        this(TEST_TABLE, DSL_CONTEXT_PROVIDER, DSL_CONTEXT_PROVIDER, NOTIFICATION_PRODUCER);
      }

      TestContext(
          Table<TestTableRecord> table,
          DslContextProvider writeDslContextProvider,
          DslContextProvider readDslContextProvider,
          DomainNotificationProducer domainNotificationProducer) {
        super(table, writeDslContextProvider, readDslContextProvider, domainNotificationProducer);
      }
    }
  }

  @Nested
  class Invocation {
    TestContext testContext;
    TestTableRecord testRecord;

    @BeforeEach
    void setUp() {
      testContext = new TestContext();

      testRecord = DSL_CONTEXT.newRecord(TEST_TABLE);
      testRecord.setId(UUID.randomUUID());
      testRecord.setCreatedAt(LocalDateTime.now());
      DSL_CONTEXT.batchInsert(testRecord).execute();
    }

    @Test
    void when_create_model_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(IllegalArgumentException.class, () -> testContext.createModel(null));
    }

    @Test
    void when_create_model_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var command = new AnotherCreateDomainCommand();
      assertThrows(UnsupportedOperationException.class, () -> testContext.createModel(command));
    }

    @Test
    void when_create_model_argument_is_correct_new_record_should_be_created() {
      final var command = new DomainCommandHandlerTest.Create.TestDomainCommand();
      final var result = assertDoesNotThrow(() -> testContext.createModel(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertEquals(
          1,
          DSL_CONTEXT
              .selectCount()
              .from(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(result.getId()))
              .fetchOne(0, int.class));
    }

    @Test
    void when_batch_create_model_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(IllegalArgumentException.class, () -> testContext.batchCreateModels(null));
    }

    @Test
    void when_batch_create_model_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var command = new AnotherBatchCreateDomainCommand();
      assertThrows(
          UnsupportedOperationException.class, () -> testContext.batchCreateModels(command));
    }

    @Test
    void when_batch_create_model_argument_is_correct_new_record_should_be_created() {
      final var command = new DomainCommandHandlerTest.BatchCreate.TestDomainCommand();
      final var result = assertDoesNotThrow(() -> testContext.batchCreateModels(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(
          1,
          DSL_CONTEXT
              .selectCount()
              .from(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(result.get(0).getId()))
              .fetchOne(0, int.class));
    }

    @Test
    void when_update_model_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(IllegalArgumentException.class, () -> testContext.updateModel(null));
    }

    @Test
    void when_update_model_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var command = new AnotherUpdateDomainCommand(testRecord.getId());
      assertThrows(UnsupportedOperationException.class, () -> testContext.updateModel(command));
    }

    @Test
    void when_update_model_argument_has_incorrect_values_illegal_argument_must_be_thrown() {
      final var javaNullCondition =
          new DomainCommandHandlerTest.Update.TestDomainCommand((Condition) null);
      assertThrows(
          IllegalArgumentException.class, () -> testContext.updateModel(javaNullCondition));

      final var noCondition =
          new DomainCommandHandlerTest.Update.TestDomainCommand(DSL.noCondition());
      assertThrows(IllegalArgumentException.class, () -> testContext.updateModel(noCondition));

      final var nullCondition =
          new DomainCommandHandlerTest.Update.TestDomainCommand(DSL.nullCondition());
      assertThrows(IllegalArgumentException.class, () -> testContext.updateModel(nullCondition));

      final var trueCondition =
          new DomainCommandHandlerTest.Update.TestDomainCommand(DSL.trueCondition());
      assertThrows(IllegalArgumentException.class, () -> testContext.updateModel(trueCondition));

      final var falseCondition =
          new DomainCommandHandlerTest.Update.TestDomainCommand(DSL.falseCondition());
      assertThrows(IllegalArgumentException.class, () -> testContext.updateModel(falseCondition));
    }

    @Test
    void when_update_model_argument_has_id_new_record_should_be_created() {
      final var command =
          new DomainCommandHandlerTest.Update.TestDomainCommand(
              TEST_TABLE.ID.eq(testRecord.getId()));
      final var result = assertDoesNotThrow(() -> testContext.updateModel(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertTrue(result.isPresent());
      assertNotEquals(
          testRecord.getCreatedAt(),
          DSL_CONTEXT
              .selectFrom(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(testRecord.getId()))
              .fetchOne()
              .getCreatedAt());
    }

    @Test
    void when_update_model_argument_has_condition_new_record_should_be_created() {
      final var command =
          new DomainCommandHandlerTest.Update.TestDomainCommand(
              TEST_TABLE.ID.eq(testRecord.getId()));
      final var result = assertDoesNotThrow(() -> testContext.updateModel(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertTrue(result.isPresent());
      assertNotEquals(
          testRecord.getCreatedAt(),
          DSL_CONTEXT
              .selectFrom(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(testRecord.getId()))
              .fetchOne()
              .getCreatedAt());
    }

    @Test
    void when_batch_update_model_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(IllegalArgumentException.class, () -> testContext.batchUpdateModels(null));
    }

    @Test
    void when_batch_update_model_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var command = new AnotherBatchUpdateDomainCommand(testRecord.getId());
      assertThrows(
          UnsupportedOperationException.class, () -> testContext.batchUpdateModels(command));
    }

    @Test
    void when_batch_update_model_argument_has_incorrect_values_illegal_argument_must_be_thrown() {
      final var javaNullCondition =
          new DomainCommandHandlerTest.BatchUpdate.TestDomainCommand((Condition) null);
      assertThrows(
          IllegalArgumentException.class, () -> testContext.batchUpdateModels(javaNullCondition));

      final var nullCondition =
          new DomainCommandHandlerTest.BatchUpdate.TestDomainCommand(DSL.nullCondition());
      assertThrows(
          IllegalArgumentException.class, () -> testContext.batchUpdateModels(nullCondition));

      final var falseCondition =
          new DomainCommandHandlerTest.BatchUpdate.TestDomainCommand(DSL.falseCondition());
      assertThrows(
          IllegalArgumentException.class, () -> testContext.batchUpdateModels(falseCondition));
    }

    @Test
    void when_batch_update_model_argument_has_id_new_record_should_be_created() {
      final var command =
          new DomainCommandHandlerTest.BatchUpdate.TestDomainCommand(
              TEST_TABLE.ID.eq(testRecord.getId()));
      final var result = assertDoesNotThrow(() -> testContext.batchUpdateModels(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertEquals(1, result.size());
      assertNotEquals(
          testRecord.getCreatedAt(),
          DSL_CONTEXT
              .selectFrom(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(testRecord.getId()))
              .fetchOne()
              .getCreatedAt());
    }

    @Test
    void when_batch_update_model_argument_has_condition_new_record_should_be_created() {
      final var command =
          new DomainCommandHandlerTest.BatchUpdate.TestDomainCommand(
              TEST_TABLE.ID.eq(testRecord.getId()));
      final var result = assertDoesNotThrow(() -> testContext.batchUpdateModels(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertEquals(1, result.size());
      assertNotEquals(
          testRecord.getCreatedAt(),
          DSL_CONTEXT
              .selectFrom(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(testRecord.getId()))
              .fetchOne()
              .getCreatedAt());
    }

    @Test
    void when_delete_model_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(IllegalArgumentException.class, () -> testContext.deleteModel(null));
    }

    @Test
    void when_delete_model_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var command = new AnotherDeleteDomainCommand(testRecord.getId());
      assertThrows(UnsupportedOperationException.class, () -> testContext.deleteModel(command));
    }

    @Test
    void when_delete_model_argument_has_incorrect_values_illegal_argument_must_be_thrown() {
      final var javaNullCondition =
          new DomainCommandHandlerTest.Delete.TestDomainCommand((Condition) null);
      assertThrows(
          IllegalArgumentException.class, () -> testContext.deleteModel(javaNullCondition));

      final var noCondition =
          new DomainCommandHandlerTest.Delete.TestDomainCommand(DSL.noCondition());
      assertThrows(IllegalArgumentException.class, () -> testContext.deleteModel(noCondition));

      final var nullCondition =
          new DomainCommandHandlerTest.Delete.TestDomainCommand(DSL.nullCondition());
      assertThrows(IllegalArgumentException.class, () -> testContext.deleteModel(nullCondition));

      final var trueCondition =
          new DomainCommandHandlerTest.Delete.TestDomainCommand(DSL.trueCondition());
      assertThrows(IllegalArgumentException.class, () -> testContext.deleteModel(trueCondition));

      final var falseCondition =
          new DomainCommandHandlerTest.Delete.TestDomainCommand(DSL.falseCondition());
      assertThrows(IllegalArgumentException.class, () -> testContext.deleteModel(falseCondition));
    }

    @Test
    void when_delete_model_argument_has_id_new_record_should_be_created() {
      final var command =
          new DomainCommandHandlerTest.Delete.TestDomainCommand(
              TEST_TABLE.ID.eq(testRecord.getId()));
      final var result = assertDoesNotThrow(() -> testContext.deleteModel(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertTrue(result.isPresent());
      assertEquals(
          0,
          DSL_CONTEXT
              .selectCount()
              .from(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(testRecord.getId()))
              .fetchOne(0, int.class));
    }

    @Test
    void when_delete_model_argument_has_condition_new_record_should_be_created() {
      final var command =
          new DomainCommandHandlerTest.Delete.TestDomainCommand(
              TEST_TABLE.ID.eq(testRecord.getId()));
      final var result = assertDoesNotThrow(() -> testContext.deleteModel(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertTrue(result.isPresent());
      assertEquals(
          0,
          DSL_CONTEXT
              .selectCount()
              .from(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(testRecord.getId()))
              .fetchOne(0, int.class));
    }

    @Test
    void when_batch_delete_model_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(IllegalArgumentException.class, () -> testContext.batchDeleteModels(null));
    }

    @Test
    void when_batch_delete_model_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var command = new AnotherBatchDeleteDomainCommand(testRecord.getId());
      assertThrows(
          UnsupportedOperationException.class, () -> testContext.batchDeleteModels(command));
    }

    @Test
    void when_batch_delete_model_argument_has_incorrect_values_illegal_argument_must_be_thrown() {
      final var javaNullCondition =
          new DomainCommandHandlerTest.BatchDelete.TestDomainCommand((Condition) null);
      assertThrows(
          IllegalArgumentException.class, () -> testContext.batchDeleteModels(javaNullCondition));

      final var nullCondition =
          new DomainCommandHandlerTest.BatchDelete.TestDomainCommand(DSL.nullCondition());
      assertThrows(
          IllegalArgumentException.class, () -> testContext.batchDeleteModels(nullCondition));

      final var falseCondition =
          new DomainCommandHandlerTest.BatchDelete.TestDomainCommand(DSL.falseCondition());
      assertThrows(
          IllegalArgumentException.class, () -> testContext.batchDeleteModels(falseCondition));
    }

    @Test
    void when_batch_delete_model_argument_has_id_new_record_should_be_created() {
      final var command =
          new DomainCommandHandlerTest.BatchDelete.TestDomainCommand(
              TEST_TABLE.ID.eq(testRecord.getId()));
      final var result = assertDoesNotThrow(() -> testContext.batchDeleteModels(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(
          0,
          DSL_CONTEXT
              .selectCount()
              .from(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(testRecord.getId()))
              .fetchOne(0, int.class));
    }

    @Test
    void when_batch_delete_model_argument_has_condition_new_record_should_be_created() {
      final var command =
          new DomainCommandHandlerTest.BatchDelete.TestDomainCommand(
              TEST_TABLE.ID.eq(testRecord.getId()));
      final var result = assertDoesNotThrow(() -> testContext.batchDeleteModels(command));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(
          0,
          DSL_CONTEXT
              .selectCount()
              .from(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(testRecord.getId()))
              .fetchOne(0, int.class));
    }

    @Test
    void when_query_one_model_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(IllegalArgumentException.class, () -> testContext.queryOneModel(null));
    }

    @Test
    void when_query_one_model_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var query = new AnotherOneQuery();
      assertThrows(UnsupportedOperationException.class, () -> testContext.queryOneModel(query));
    }

    @Test
    void when_query_one_model_argument_is_correct_new_record_should_be_created() {
      final var query = new DomainQueryHandlerTest.One.TestQuery();
      final var result = assertDoesNotThrow(() -> testContext.queryOneModel(query));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertTrue(result.isPresent());
      assertEquals(
          1,
          DSL_CONTEXT
              .selectCount()
              .from(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(result.get().getId()))
              .fetchOne(0, int.class));
    }

    @Test
    void when_query_many_models_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(IllegalArgumentException.class, () -> testContext.queryManyModels(null));
    }

    @Test
    void when_query_many_models_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var query = new AnotherManyQuery();
      assertThrows(UnsupportedOperationException.class, () -> testContext.queryManyModels(query));
    }

    @Test
    void when_query_many_models_argument_is_correct_new_record_should_be_created() {
      final var query = new DomainQueryHandlerTest.Many.TestQuery();
      final var result = assertDoesNotThrow(() -> testContext.queryManyModels(query));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(
          1,
          DSL_CONTEXT
              .selectCount()
              .from(TEST_TABLE)
              .where(TEST_TABLE.ID.eq(result.get(0).getId()))
              .fetchOne(0, int.class));
    }

    @Test
    void when_view_one_model_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(
          IllegalArgumentException.class, () -> testContext.viewOneModel(null, Object.class));

      final var view = new DomainViewHandlerTest.One.TestView();
      assertThrows(IllegalArgumentException.class, () -> testContext.viewOneModel(view, null));
    }

    @Test
    void when_view_one_model_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var view = new AnotherOneView();
      assertThrows(
          UnsupportedOperationException.class, () -> testContext.viewOneModel(view, Object.class));
    }

    @Test
    void when_view_one_model_argument_has_different_output_illegal_state_must_be_thrown() {
      final var view = new DomainViewHandlerTest.One.TestView();
      assertThrows(
          IllegalStateException.class, () -> testContext.viewOneModel(view, Integer.class));
    }

    @Test
    void when_view_one_model_argument_is_correct_new_record_should_be_created() {
      final var view = new DomainViewHandlerTest.One.TestView();
      final var result = assertDoesNotThrow(() -> testContext.viewOneModel(view, Object.class));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertTrue(result.isPresent());
    }

    @Test
    void when_view_many_models_argument_is_null_illegal_argument_must_be_thrown() {
      assertThrows(
          IllegalArgumentException.class, () -> testContext.viewManyModels(null, Object.class));

      final var view = new DomainViewHandlerTest.Many.TestView();
      assertThrows(IllegalArgumentException.class, () -> testContext.viewManyModels(view, null));
    }

    @Test
    void when_view_many_models_argument_has_no_handler_unsupported_operation_must_be_thrown() {
      final var view = new AnotherManyView();
      assertThrows(
          UnsupportedOperationException.class,
          () -> testContext.viewManyModels(view, Object.class));
    }

    @Test
    void when_view_many_model_argument_has_different_output_illegal_state_must_be_thrown() {
      final var view = new DomainViewHandlerTest.Many.TestView();
      assertThrows(
          IllegalStateException.class, () -> testContext.viewManyModels(view, Integer.class));
    }

    @Test
    void when_view_many_models_argument_is_correct_new_record_should_be_created() {
      final var view = new DomainViewHandlerTest.Many.TestView();
      final var result = assertDoesNotThrow(() -> testContext.viewManyModels(view, Object.class));

      assertFalse(testContext.isAnyReadLockHeld());
      assertNotNull(result);
      assertEquals(1, result.size());
    }

    /** Test context to play around with. */
    static class TestContext extends BoundedContext<TestTableRecord> {
      TestContext() {
        super(TEST_TABLE, DSL_CONTEXT_PROVIDER, DSL_CONTEXT_PROVIDER, NOTIFICATION_PRODUCER);

        addDomainCommandHandler(
            new DomainCommandHandlerTest.Create.TestHandler(
                DomainCommandHandlerTest.Create.TestDomainCommand.class));
        addDomainCommandHandler(
            new DomainCommandHandlerTest.Update.TestHandler(
                DomainCommandHandlerTest.Update.TestDomainCommand.class));
        addDomainCommandHandler(
            new DomainCommandHandlerTest.Delete.TestHandler(
                DomainCommandHandlerTest.Delete.TestDomainCommand.class));
        addDomainCommandHandler(
            new DomainCommandHandlerTest.BatchCreate.TestHandler(
                DomainCommandHandlerTest.BatchCreate.TestDomainCommand.class));
        addDomainCommandHandler(
            new DomainCommandHandlerTest.BatchUpdate.TestHandler(
                DomainCommandHandlerTest.BatchUpdate.TestDomainCommand.class));
        addDomainCommandHandler(
            new DomainCommandHandlerTest.BatchDelete.TestHandler(
                DomainCommandHandlerTest.BatchDelete.TestDomainCommand.class));

        addDomainQueryHandler(
            new DomainQueryHandlerTest.One.TestHandler(DomainQueryHandlerTest.One.TestQuery.class));
        addDomainQueryHandler(
            new DomainQueryHandlerTest.Many.TestHandler(
                DomainQueryHandlerTest.Many.TestQuery.class));

        addDomainViewHandler(
            new DomainViewHandlerTest.One.TestHandler(
                DomainViewHandlerTest.One.TestView.class, Object.class));
        addDomainViewHandler(
            new DomainViewHandlerTest.Many.TestHandler(
                DomainViewHandlerTest.Many.TestView.class, Object.class));
      }
    }

    /** Test command implementation. */
    record AnotherCreateDomainCommand(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainCommand.Create<UUID, Instant> {
      AnotherCreateDomainCommand() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test command implementation. */
    record AnotherUpdateDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient, UUID id)
        implements DomainCommand.Update<UUID, Instant> {
      AnotherUpdateDomainCommand(UUID id) {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance(), id);
      }

      @Override
      public Condition condition() {
        return TEST_TABLE.ID.eq(id);
      }
    }

    /** Test command implementation. */
    record AnotherDeleteDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient, UUID id)
        implements DomainCommand.Delete<UUID, Instant> {
      AnotherDeleteDomainCommand(UUID id) {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance(), id);
      }

      @Override
      public Condition condition() {
        return TEST_TABLE.ID.eq(id);
      }
    }

    /** Test command implementation. */
    record AnotherBatchCreateDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainCommand.BatchCreate<UUID, Instant> {
      AnotherBatchCreateDomainCommand() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test command implementation. */
    record AnotherBatchUpdateDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient, Collection<UUID> ids)
        implements DomainCommand.BatchUpdate<UUID, Instant> {
      AnotherBatchUpdateDomainCommand(UUID id) {
        this(
            UUID.randomUUID(),
            Instant.now(),
            AnonymousDomainClient.getInstance(),
            Collections.singleton(id));
      }

      @Override
      public Condition condition() {
        return TEST_TABLE.ID.in(ids);
      }
    }

    /** Test command implementation. */
    record AnotherBatchDeleteDomainCommand(
        UUID messageId, Instant createdAt, DomainClient domainClient, Collection<UUID> ids)
        implements DomainCommand.BatchDelete<UUID, Instant> {
      AnotherBatchDeleteDomainCommand(UUID id) {
        this(
            UUID.randomUUID(),
            Instant.now(),
            AnonymousDomainClient.getInstance(),
            Collections.singleton(id));
      }

      @Override
      public Condition condition() {
        return TEST_TABLE.ID.in(ids);
      }
    }

    /** Test query implementation. */
    record AnotherOneQuery(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainQuery.One<UUID, Instant> {
      AnotherOneQuery() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test query implementation. */
    record AnotherManyQuery(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainQuery.Many<UUID, Instant> {
      AnotherManyQuery() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test view implementation. */
    record AnotherOneView(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainView.One<UUID, Instant> {
      AnotherOneView() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }

    /** Test view implementation. */
    record AnotherManyView(UUID messageId, Instant createdAt, DomainClient domainClient)
        implements DomainView.Many<UUID, Instant> {
      AnotherManyView() {
        this(UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());
      }
    }
  }
}
