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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.UpdatableRecord;

/**
 * Class to accept and process the work associated to a specific {@link DomainQuery}:
 *
 * <ul>
 *   <li>Assert that the {@link DomainClient} can invoke the {@link DomainQuery}.
 *   <li>Read the database state.
 *   <li><b>Optional</b>: emit a {@link DomainNotification} if {@link DomainQuery} succeeded.
 *   <li><b>Optional</b>: emit a {@link DomainNotification} if {@link DomainQuery} failed.
 * </ul>
 *
 * <p>Because {@link DomainQuery} leverages new Java {@code sealed} feature, for more type safety
 * this class also makes use of the same feature.
 *
 * <p><b>Design note</b>: whichever parameters can be controlled must be covered with null checks
 * and {@code final} (if possible), whichever parameters are expected to be provided by consumer
 * must be checked with the help of {@link Suspicious} methods.
 *
 * @param <QUERY> the type of the particular {@link DomainQuery}
 * @param <OUTPUT> the expected output type of the given query, typically {@link Optional} or {@link
 *     Stream} - containers which can be empty
 * @see <a href="https://rules.sonarsource.com/java/RSPEC-119/">Suppressed Sonar rule for the sake
 *     to have more readable type names</a>
 */
@SuppressWarnings("squid:S119")
// @formatter:off
public abstract sealed class DomainQueryHandler<
  QUERY extends DomainQuery<?, ?>,
  OUTPUT
>
extends
        DomainHandler<QUERY, OUTPUT>
permits
  DomainQueryHandler.One,
  DomainQueryHandler.Many
{
// @formatter:on
  private final Class<QUERY> queryClass;

  /**
   * Default constructor.
   *
   * @param queryClass this handler is intended for
   */
  protected DomainQueryHandler(final Class<QUERY> queryClass) {
    this.queryClass = throwIllegalArgumentIfNull(queryClass, "Query class");
  }

  /**
   * @return specific {@link DomainQuery} class
   */
  public final Class<QUERY> getQueryClass() {
    return queryClass;
  }

  /**
   * Defines business logic of this particular {@link DomainQueryHandler}.
   *
   * @param query being invoked
   * @param dsl created by {@link BoundedContext} to perform database querying
   * @return database records
   * @throws Exception if any happened during invocation
   * @see <a href="https://rules.sonarsource.com/java/RSPEC-112/">Suppressed Sonar rule to allow
   *     more flexibility</a>
   */
  @SuppressWarnings("squid:S112")
  protected abstract OUTPUT run(final QUERY query, final DSLContext dsl) throws Exception;

  /**
   * General business logic invocation to be used and exposed via {@link BoundedContext}.
   *
   * <p>The usage of {@link Try} will "hide" the exception that can be thrown by {@link
   * #run(DomainQuery, DSLContext)} - but not get rid of it. Actual exception along with its
   * stacktrace will be emitted by {@link BoundedContext} upon this handler invocation.
   *
   * <p>This method is package-private as it is intended to be invoked by {@link BoundedContext}
   * only.
   *
   * @param query being invoked
   * @param readWriteDsl to use for notifications
   * @param readOnlyDsl to use for the invocation
   * @param domainNotificationProducer to publish {@link DomainNotification}s with
   * @return a result of command invocation
   */
  final OUTPUT runInContext(
      final QUERY query,
      final DSLContext readWriteDsl,
      final DSLContext readOnlyDsl,
      final DomainNotificationProducer domainNotificationProducer) {
    final QUERY nonNullQuery = throwIllegalArgumentIfNull(query, "Query");
    final DomainClient nonNullDomainClient =
        throwIllegalStateIfNull(nonNullQuery.domainClient(), "Query's client");

    if (!canBeUsedBy(nonNullDomainClient)) {
      throw new UnauthorizedException(
          "Client '%s' is not allowed to use '%s' query"
              .formatted(nonNullDomainClient.domainRole(), getQueryClass().getSimpleName()));
    }

    final DSLContext nonNullReadWriteDsl = throwIllegalStateIfNull(readWriteDsl, "Read-write DSL");
    final DSLContext nonNullReadOnlyDsl = throwIllegalStateIfNull(readOnlyDsl, "Read-only DSL");

    final DomainNotificationProducer nonNullDomainNotificationProducer =
        throwIllegalStateIfNull(domainNotificationProducer, "Notification Publisher");

    final Try<OUTPUT> output =
        Try.of(
            () ->
                throwIllegalStateIfNull(
                    run(nonNullQuery, nonNullReadOnlyDsl), "Query handler result"));

    nonNullReadWriteDsl.transaction(
        (final Configuration trx) -> {
          output.ifSuccess(
              result -> {
                final var optionalNotification =
                    throwIllegalStateIfNull(
                        onSuccess(nonNullQuery, result),
                        "Query handler Optional successful notification");
                optionalNotification.ifPresent(
                    notification ->
                        nonNullDomainNotificationProducer.store(trx.dsl(), notification));
              });

          output.ifFailure(
              reason -> {
                final var optionalNotification =
                    throwIllegalStateIfNull(
                        onFailure(nonNullQuery, reason),
                        "Query handler Optional failure notification");
                optionalNotification.ifPresent(
                    notification ->
                        nonNullDomainNotificationProducer.store(trx.dsl(), notification));
              });
        });

    return output.get();
  }

  /**
   * A variant of the {@link DomainQueryHandler} for {@link DomainQuery.One}.
   *
   * @param <ONE> the type of the particular {@link DomainQuery.One}
   * @param <RECORD> the database record type generated by jOOQ
   */
  // @formatter:off
  public abstract static non-sealed class One<
    ONE extends DomainQuery.One<?,  ?>,
    RECORD extends UpdatableRecord<RECORD>
  > extends DomainQueryHandler<ONE, Optional<RECORD>> {
  // @formatter:on
    protected One(final Class<ONE> queryClass) {
      super(queryClass);
    }
  }

  /**
   * A variant of the {@link DomainQueryHandler} for {@link DomainQuery.Many}.
   *
   * @param <MANY> the type of the particular {@link DomainQuery.Many}
   * @param <RECORD> the database record type generated by jOOQ
   */
  // @formatter:off
  public abstract static non-sealed class Many<
    MANY extends DomainQuery.Many<?,  ?>,
    RECORD extends UpdatableRecord<RECORD>
  > extends DomainQueryHandler<MANY, List<RECORD>> {
  // @formatter:on
    protected Many(final Class<MANY> queryClass) {
      super(queryClass);
    }
  }
}
