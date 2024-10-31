/*
 * Copyright 2024 Roman Khlebnov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.suppierk.ddd.cqrs;

import io.github.suppierk.ddd.async.DomainNotification;
import io.github.suppierk.ddd.async.DomainNotificationProducer;
import io.github.suppierk.ddd.authorization.DomainClient;
import io.github.suppierk.ddd.authorization.UnauthorizedException;
import io.github.suppierk.java.Try;
import io.github.suppierk.java.UnsafeFunctions;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;

/**
 * Class to accept and process the work associated to a specific {@link DomainCommand}:
 *
 * <ul>
 *   <li>Assert that the {@link DomainClient} can invoke the {@link DomainCommand}.
 *   <li>Modify the database state.
 *   <li><b>Optional</b>: emit a {@link DomainNotification} if {@link DomainCommand} succeeded.
 *   <li><b>Optional</b>: emit a {@link DomainNotification} if {@link DomainCommand} failed.
 * </ul>
 *
 * <p>Because {@link DomainCommand} leverages new Java {@code sealed} feature, for more type safety
 * this class also makes use of the same feature.
 *
 * <p><b>Design note</b>: whichever parameters can be controlled must be covered with null checks
 * and {@code final} (if possible), whichever parameters are expected to be provided by consumer
 * must be checked with the help of {@link Suspicious} methods.
 *
 * @param <COMMAND> the type of the particular {@link DomainCommand}
 * @param <RECORD> type of the database records
 * @param <OUTPUT> the expected output type of the given command, typically {@link Optional} or
 *     {@link Stream} - containers which can be empty
 * @see <a href="https://rules.sonarsource.com/java/RSPEC-119/">Suppressed Sonar rule for the sake
 *     to have more readable type names</a>
 */
@SuppressWarnings("squid:S119")
// @formatter:off
public abstract sealed class DomainCommandHandler<
  COMMAND extends DomainCommand<?, ?>,
  RECORD extends Record,
  OUTPUT
>
extends
        DomainHandler<COMMAND, OUTPUT>
permits
  DomainCommandHandler.Create, DomainCommandHandler.BatchCreate,
  DomainCommandHandler.Update, DomainCommandHandler.BatchUpdate,
  DomainCommandHandler.Delete, DomainCommandHandler.BatchDelete
{
// @formatter:on
  private static final String MISSING_COMMAND_HANDLER_OPTIONAL_SUCCESSFUL_NOTIFICATION =
      "Command handler Optional successful notification";
  private static final String MISSING_COMMAND_HANDLER_OPTIONAL_FAILURE_NOTIFICATION =
      "Command handler Optional failure notification";

  private final Class<COMMAND> commandClass;

  /**
   * Constructs a new {@link DomainCommandHandler} for a specific {@link DomainCommand} class.
   *
   * @param commandClass the class of the {@link DomainCommand} to handle
   * @throws IllegalArgumentException if the command class is null
   */
  protected DomainCommandHandler(final Class<COMMAND> commandClass) {
    this.commandClass = throwIllegalArgumentIfNull(commandClass, "Command class");
  }

  /**
   * Returns the class type of the command being handled by this {@link DomainCommandHandler}.
   *
   * @return the class type of the command
   */
  public final Class<COMMAND> getCommandClass() {
    return commandClass;
  }

  /**
   * Executes the core logic of the command.
   *
   * @param command being executed
   * @param readWriteDsl for read and write operations
   * @param readOnlyDsl for read-only operations
   * @param table on which operations are performed
   * @param condition for querying the database table
   * @param domainNotificationProducer for publishing notifications
   * @return the result of the command execution
   */
  protected abstract OUTPUT internalRunContract(
      final COMMAND command,
      final DSLContext readWriteDsl,
      final DSLContext readOnlyDsl,
      final Table<RECORD> table,
      final Condition condition,
      final DomainNotificationProducer domainNotificationProducer);

  /**
   * For certain methods, it is important that the condition is not one of:
   *
   * <ul>
   *   <li>{@link DSL#noCondition()}
   *   <li>{@link DSL#nullCondition()}
   *   <li>{@link DSL#trueCondition()}
   *   <li>{@link DSL#falseCondition()}
   * </ul>
   *
   * <p>Depending on the command, the list of excluded conditions may vary.
   *
   * @param condition to verify
   * @param excludedConditions the input should not match with
   * @throws IllegalArgumentException if condition did not pass the check
   */
  protected final void verifyCondition(
      final Condition condition, final Condition... excludedConditions) {
    for (Condition excludedCondition : excludedConditions) {
      if (condition.equals(excludedCondition)) {
        throw new IllegalArgumentException(
            "DSL.%s is not allowed"
                .formatted(decapitalize(excludedCondition.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the given command within a transactional context.
   *
   * @param command to be executed
   * @param readWriteDsl for read and write operations within the transactional context
   * @param readOnlyDsl for read-only operations
   * @param table on which operations are to be performed
   * @param condition for querying the database table
   * @param domainNotificationProducer for publishing notifications
   * @return the result of the command execution
   * @throws IllegalArgumentException if any command parameter is null
   * @throws IllegalStateException if any internal state is invalid (typically null)
   * @throws UnauthorizedException if the client is not authorized to execute the command
   */
  final OUTPUT runInContext(
      final COMMAND command,
      final DSLContext readWriteDsl,
      final DSLContext readOnlyDsl,
      final Table<RECORD> table,
      final Condition condition,
      final DomainNotificationProducer domainNotificationProducer) {
    final COMMAND nonNullCommand = throwIllegalArgumentIfNull(command, "Command");
    final DomainClient nonNullDomainClient =
        throwIllegalStateIfNull(nonNullCommand.domainClient(), "Command's client");

    if (!canBeUsedBy(nonNullDomainClient)) {
      throw new UnauthorizedException(
          "Client '%s' is not allowed to use '%s' command"
              .formatted(nonNullDomainClient.domainRole(), getCommandClass().getSimpleName()));
    }

    final Table<RECORD> nonNullTable = throwIllegalArgumentIfNull(table, "Table");
    final Condition nonNullCondition =
        throwIllegalArgumentIfNull(condition, "Command DSL condition");

    final DSLContext nonNullReadWriteDsl = throwIllegalStateIfNull(readWriteDsl, "Read-write DSL");
    final DSLContext nonNullReadOnlyDsl = throwIllegalStateIfNull(readOnlyDsl, "Read-only DSL");

    final DomainNotificationProducer nonNullDomainNotificationProducer =
        throwIllegalStateIfNull(domainNotificationProducer, "Notification Publisher");

    return nonNullReadWriteDsl.transactionResult(
        (final Configuration trx) ->
            internalRunContract(
                nonNullCommand,
                trx.dsl(),
                nonNullReadOnlyDsl,
                nonNullTable,
                nonNullCondition,
                nonNullDomainNotificationProducer));
  }

  /**
   * Small utility to make first character in class name in lower case.
   *
   * <p>Has a very niche use case, its usage is controlled, and it is not meant to be exposed.
   *
   * @param input string to de-capitalize
   * @return string with the first letter in lower case
   * @see <a
   *     href="https://stackoverflow.com/questions/4052840/most-efficient-way-to-make-the-first-character-of-a-string-lower-case">StackOverflow
   *     related question</a>
   */
  final String decapitalize(final String input) {
    char[] charArray = input.toCharArray();
    charArray[0] = Character.toLowerCase(charArray[0]);
    return new String(charArray);
  }

  /**
   * A variant of the {@link DomainCommandHandler} for {@link DomainCommand.Create}.
   *
   * @param <CREATE> the type of the particular {@link DomainCommand.Create}
   * @param <RECORD> the database record type generated by jOOQ
   */
  // @formatter:off
  public abstract static non-sealed class Create<
    CREATE extends DomainCommand.Create<?, ?>,
    RECORD extends UpdatableRecord<RECORD>
  > extends DomainCommandHandler<CREATE, RECORD, RECORD> {
  // @formatter:on
    protected Create(final Class<CREATE> commandClass) {
      super(commandClass);
    }

    /**
     * Business logic to fill in values in blank record for insertion.
     *
     * @param command containing the data required to create the new record
     * @param blankRecord template to be filled
     * @return the new database record with the populated values after it has been saved to the
     *     database
     * @throws Exception if any error occurs during the execution of the command
     * @see <a href="https://rules.sonarsource.com/java/RSPEC-112/">Suppressed Sonar rule to allow
     *     more flexibility</a>
     */
    @SuppressWarnings("squid:S112")
    protected abstract RECORD fillBlankRecord(final CREATE command, final RECORD blankRecord)
        throws Exception;

    /** {@inheritDoc} */
    @Override
    protected final RECORD internalRunContract(
        final CREATE command,
        final DSLContext readWriteDsl,
        final DSLContext readOnlyDsl,
        final Table<RECORD> table,
        final Condition condition,
        final DomainNotificationProducer domainNotificationProducer) {
      final RECORD blankRecord = readWriteDsl.newRecord(table);
      blankRecord.detach(); // Prohibit user from invoking write operations

      final Try<RECORD> output =
          Try.of(
              () -> {
                final var dbRecord =
                    throwIllegalStateIfNull(
                        fillBlankRecord(command, blankRecord), "New database record");
                readWriteDsl.batchInsert(dbRecord).execute();
                dbRecord.attach(
                    readOnlyDsl.configuration()); // Permit user to invoke read operations
                return dbRecord;
              });

      output.ifSuccess(
          dbRecord -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onSuccess(command, dbRecord),
                    MISSING_COMMAND_HANDLER_OPTIONAL_SUCCESSFUL_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      output.ifFailure(
          reason -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onFailure(command, reason),
                    MISSING_COMMAND_HANDLER_OPTIONAL_FAILURE_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      return output.get();
    }
  }

  /**
   * A variant of the {@link DomainCommandHandler} for {@link DomainCommand.Update}.
   *
   * @param <UPDATE> the type of the particular {@link DomainCommand.Update}
   * @param <RECORD> the database record type generated by jOOQ
   */
  // @formatter:off
  public abstract static non-sealed class Update<
    UPDATE extends DomainCommand.Update<?, ?>,
    RECORD extends UpdatableRecord<RECORD>
  > extends DomainCommandHandler<UPDATE, RECORD, Optional<RECORD>> {
  // @formatter:on
    protected Update(final Class<UPDATE> commandClass) {
      super(commandClass);
    }

    /**
     * Business logic to update an existing database record based on the provided command.
     *
     * @param command containing the data required to update the record
     * @param oldRecord to be updated
     * @return the updated database record with the populated values after it has been saved to the
     *     database
     * @throws Exception if any error occurs during the execution of the command.
     * @see <a href="https://rules.sonarsource.com/java/RSPEC-112/">Suppressed Sonar rule to allow
     *     more flexibility</a>
     */
    @SuppressWarnings("squid:S112")
    protected abstract RECORD updateRecordValues(final UPDATE command, final RECORD oldRecord)
        throws Exception;

    /** {@inheritDoc} */
    @Override
    protected final Optional<RECORD> internalRunContract(
        final UPDATE command,
        final DSLContext readWriteDsl,
        final DSLContext readOnlyDsl,
        final Table<RECORD> table,
        final Condition condition,
        final DomainNotificationProducer domainNotificationProducer) {
      verifyCondition(
          condition,
          DSL.noCondition(),
          DSL.nullCondition(),
          DSL.trueCondition(),
          DSL.falseCondition());

      final Try<Optional<RECORD>> output =
          Try.of(
              () ->
                  readWriteDsl
                      .fetchOptional(table, condition)
                      .map(
                          UnsafeFunctions.unsafeFunction(
                              dbRecord -> {
                                dbRecord.detach(); // Prohibit user from invoking write operations
                                final var updatedRecord =
                                    throwIllegalStateIfNull(
                                        updateRecordValues(command, dbRecord),
                                        "Updated database record");
                                readWriteDsl.batchUpdate(updatedRecord).execute();
                                updatedRecord.attach(
                                    readOnlyDsl
                                        .configuration()); // Permit user to invoke read operations
                                return updatedRecord;
                              })));

      output.ifSuccess(
          optionalRecord -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onSuccess(command, optionalRecord),
                    MISSING_COMMAND_HANDLER_OPTIONAL_SUCCESSFUL_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      output.ifFailure(
          reason -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onFailure(command, reason),
                    MISSING_COMMAND_HANDLER_OPTIONAL_FAILURE_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      return output.get();
    }
  }

  /**
   * A variant of the {@link DomainCommandHandler} for {@link DomainCommand.Delete}.
   *
   * <p>Deleted record is explicitly detached from jOOQ {@link Configuration}, preventing further
   * operations.
   *
   * @param <DELETE> the type of the particular {@link DomainCommand.Delete}
   * @param <RECORD> the database record type generated by jOOQ
   */
  // @formatter:off
  public abstract static non-sealed class Delete<
    DELETE extends DomainCommand.Delete<?, ?>,
    RECORD extends UpdatableRecord<RECORD>
  > extends DomainCommandHandler<DELETE, RECORD, Optional<RECORD>> {
  // @formatter:on
    protected Delete(final Class<DELETE> commandClass) {
      super(commandClass);
    }

    /**
     * Business logic to run before the record is deleted.
     *
     * @param command containing the data required to delete the record
     * @param dbRecord to be deleted
     * @throws Exception if any error occurs during the execution of the command.
     * @see <a href="https://rules.sonarsource.com/java/RSPEC-112/">Suppressed Sonar rule to allow
     *     more flexibility</a>
     */
    @SuppressWarnings("squid:S112")
    protected void beforeDelete(final DELETE command, final RECORD dbRecord) throws Exception {
      // No action by default
    }

    /** {@inheritDoc} */
    @Override
    protected final Optional<RECORD> internalRunContract(
        final DELETE command,
        final DSLContext readWriteDsl,
        final DSLContext readOnlyDsl,
        final Table<RECORD> table,
        final Condition condition,
        final DomainNotificationProducer domainNotificationProducer) {
      verifyCondition(
          condition,
          DSL.noCondition(),
          DSL.nullCondition(),
          DSL.trueCondition(),
          DSL.falseCondition());

      final Try<Optional<RECORD>> output =
          Try.of(
              () ->
                  readWriteDsl
                      .fetchOptional(table, condition)
                      .map(
                          UnsafeFunctions.unsafeFunction(
                              dbRecord -> {
                                dbRecord.detach(); // Prohibit user from invoking write operations
                                beforeDelete(command, dbRecord);
                                readWriteDsl.batchDelete(dbRecord).execute();
                                dbRecord.detach(); // Ensure no operations can be performed after
                                // deletion
                                return dbRecord;
                              })));

      output.ifSuccess(
          optionalRecord -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onSuccess(command, optionalRecord),
                    MISSING_COMMAND_HANDLER_OPTIONAL_SUCCESSFUL_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      output.ifFailure(
          reason -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onFailure(command, reason),
                    MISSING_COMMAND_HANDLER_OPTIONAL_FAILURE_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      return output.get();
    }
  }

  /**
   * A variant of the {@link DomainCommandHandler} for {@link DomainCommand.BatchCreate}.
   *
   * <p>This handler provides the ability to have virtually unbounded {@link Stream} of new database
   * records.
   *
   * @param <BATCH_CREATE> the type of the particular {@link DomainCommand.BatchCreate}
   * @param <RECORD> the database record type generated by jOOQ
   */
  // @formatter:off
  public abstract static non-sealed class BatchCreate<
    BATCH_CREATE extends DomainCommand.BatchCreate<?, ?>,
    RECORD extends UpdatableRecord<RECORD>
  > extends DomainCommandHandler<BATCH_CREATE, RECORD, List<RECORD>> {
  // @formatter:on
    protected BatchCreate(final Class<BATCH_CREATE> commandClass) {
      super(commandClass);
    }

    /**
     * Business logic for creating a new database record stream based on the provided command and
     * blank record template supplier.
     *
     * @param command containing the data required to create the new record
     * @param blankRecordSupplier to fetch templates to be filled on demand
     * @return new database records with the populated values after they have been saved to the
     *     database
     * @throws Exception if any error occurs during the execution of the command
     * @see <a href="https://rules.sonarsource.com/java/RSPEC-112/">Suppressed Sonar rule to allow
     *     more flexibility</a>
     */
    @SuppressWarnings("squid:S112")
    protected abstract Stream<RECORD> fillBlankRecords(
        final BATCH_CREATE command, final Supplier<RECORD> blankRecordSupplier) throws Exception;

    /** {@inheritDoc} */
    @Override
    protected final List<RECORD> internalRunContract(
        final BATCH_CREATE command,
        final DSLContext readWriteDsl,
        final DSLContext readOnlyDsl,
        final Table<RECORD> table,
        final Condition condition,
        final DomainNotificationProducer domainNotificationProducer) {
      final Supplier<RECORD> blankRecordSupplier =
          () -> {
            final RECORD blankRecord = readWriteDsl.newRecord(table);
            blankRecord.detach(); // Prohibit user from invoking write operations
            return blankRecord;
          };

      final Try<List<RECORD>> output =
          Try.of(
              () -> {
                final var dbRecords =
                    throwIllegalStateIfNull(
                            fillBlankRecords(command, blankRecordSupplier),
                            "New database records stream")
                        .toList();

                readWriteDsl.batchInsert(dbRecords).execute();

                for (RECORD dbRecord : dbRecords) {
                  dbRecord.attach(
                      readOnlyDsl.configuration()); // Permit user to invoke read operations
                }

                return dbRecords;
              });

      output.ifSuccess(
          dbRecords -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onSuccess(command, dbRecords),
                    MISSING_COMMAND_HANDLER_OPTIONAL_SUCCESSFUL_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      output.ifFailure(
          reason -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onFailure(command, reason),
                    MISSING_COMMAND_HANDLER_OPTIONAL_FAILURE_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      return output.get();
    }
  }

  /**
   * A variant of the {@link DomainCommandHandler} for {@link DomainCommand.BatchUpdate}.
   *
   * @param <BATCH_UPDATE> the type of the particular {@link DomainCommand.BatchUpdate}
   * @param <RECORD> the database record type generated by jOOQ
   */
  // @formatter:off
  public abstract static non-sealed class BatchUpdate<
    BATCH_UPDATE extends DomainCommand.BatchUpdate<?, ?>,
    RECORD extends UpdatableRecord<RECORD>
  > extends DomainCommandHandler<BATCH_UPDATE, RECORD, List<RECORD>> {
  // @formatter:on
    protected BatchUpdate(final Class<BATCH_UPDATE> commandClass) {
      super(commandClass);
    }

    /**
     * Business logic to update existing database records based on the provided command.
     *
     * @param command containing the data required to update records
     * @param oldRecords to be updated
     * @return updated database records with the populated values after they have been saved to the
     *     database
     * @throws Exception if any error occurs during the execution of the command.
     * @see <a href="https://rules.sonarsource.com/java/RSPEC-112/">Suppressed Sonar rule to allow
     *     more flexibility</a>
     */
    @SuppressWarnings("squid:S112")
    protected abstract Stream<RECORD> updateRecordValues(
        final BATCH_UPDATE command, final Stream<RECORD> oldRecords) throws Exception;

    /** {@inheritDoc} */
    @Override
    protected final List<RECORD> internalRunContract(
        final BATCH_UPDATE command,
        final DSLContext readWriteDsl,
        final DSLContext readOnlyDsl,
        final Table<RECORD> table,
        final Condition condition,
        final DomainNotificationProducer domainNotificationProducer) {
      // To support updateAll type of functionality, we permit noCondition / trueCondition
      verifyCondition(condition, DSL.nullCondition(), DSL.falseCondition());

      final Try<List<RECORD>> output =
          Try.of(
              () -> {
                final var dbRecords =
                    readWriteDsl
                        .fetchStream(table, condition)
                        .map(
                            dbRecord -> {
                              dbRecord.detach(); // Prohibit user from invoking write operations
                              return dbRecord;
                            });

                final var updatedRecords =
                    throwIllegalStateIfNull(
                            updateRecordValues(command, dbRecords),
                            "Updated database records stream")
                        .toList();

                readWriteDsl.batchUpdate(updatedRecords).execute();

                for (RECORD updatedRecord : updatedRecords) {
                  updatedRecord.attach(
                      readOnlyDsl.configuration()); // Permit user to invoke read operations
                }

                return updatedRecords;
              });

      output.ifSuccess(
          dbRecords -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onSuccess(command, dbRecords),
                    MISSING_COMMAND_HANDLER_OPTIONAL_SUCCESSFUL_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      output.ifFailure(
          reason -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onFailure(command, reason),
                    MISSING_COMMAND_HANDLER_OPTIONAL_FAILURE_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      return output.get();
    }
  }

  /**
   * A variant of the {@link DomainCommandHandler} for {@link DomainCommand.BatchDelete}.
   *
   * <p>Deleted records are explicitly detached from jOOQ {@link Configuration}, preventing further
   * operations.
   *
   * @param <BATCH_DELETE> the type of the particular {@link DomainCommand.BatchDelete}
   * @param <RECORD> the database record type generated by jOOQ
   */
  // @formatter:off
  public abstract static non-sealed class BatchDelete<
    BATCH_DELETE extends DomainCommand.BatchDelete<?, ?>,
    RECORD extends UpdatableRecord<RECORD>
  > extends DomainCommandHandler<BATCH_DELETE, RECORD, List<RECORD>> {
  // @formatter:on
    protected BatchDelete(final Class<BATCH_DELETE> commandClass) {
      super(commandClass);
    }

    /**
     * Business logic to execute before existing database record stream is deleted based on the
     * provided command.
     *
     * @param command containing the data required to delete records
     * @param dbRecords to be deleted (can include filtering)
     * @throws Exception if any error occurs during the execution of the command.
     * @see <a href="https://rules.sonarsource.com/java/RSPEC-112/">Suppressed Sonar rule to allow
     *     more flexibility</a>
     */
    @SuppressWarnings("squid:S112")
    protected Stream<RECORD> beforeDelete(
        final BATCH_DELETE command, final Stream<RECORD> dbRecords) throws Exception {
      return dbRecords;
    }

    /** {@inheritDoc} */
    @Override
    protected final List<RECORD> internalRunContract(
        final BATCH_DELETE command,
        final DSLContext readWriteDsl,
        final DSLContext readOnlyDsl,
        final Table<RECORD> table,
        final Condition condition,
        final DomainNotificationProducer domainNotificationProducer) {
      // To support deleteAll type of functionality, we permit noCondition / trueCondition
      verifyCondition(condition, DSL.nullCondition(), DSL.falseCondition());

      final Try<List<RECORD>> output =
          Try.of(
              () -> {
                final var dbRecords =
                    readWriteDsl
                        .fetchStream(table, condition)
                        .map(
                            dbRecord -> {
                              dbRecord.detach(); // Prohibit user from invoking write operations
                              return dbRecord;
                            });

                final var unwantedRecords =
                    throwIllegalStateIfNull(
                            beforeDelete(command, dbRecords), "Deletable database records stream")
                        .toList();

                readWriteDsl.batchDelete(unwantedRecords).execute();

                for (RECORD updatedRecord : unwantedRecords) {
                  updatedRecord.detach(); // Ensure no operations can be performed after deletion
                }

                return unwantedRecords;
              });

      output.ifSuccess(
          dbRecords -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onSuccess(command, dbRecords),
                    MISSING_COMMAND_HANDLER_OPTIONAL_SUCCESSFUL_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      output.ifFailure(
          reason -> {
            final var optionalNotification =
                throwIllegalStateIfNull(
                    onFailure(command, reason),
                    MISSING_COMMAND_HANDLER_OPTIONAL_FAILURE_NOTIFICATION);
            optionalNotification.ifPresent(
                notification -> domainNotificationProducer.store(readWriteDsl, notification));
          });

      return output.get();
    }
  }
}
