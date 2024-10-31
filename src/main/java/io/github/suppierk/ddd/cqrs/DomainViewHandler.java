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
import org.jooq.Record;
import org.jooq.UpdatableRecord;

/**
 * Class to accept and process the work associated to a specific {@link DomainView}:
 *
 * <ul>
 *   <li>Assert that the {@link DomainClient} can invoke the {@link DomainView}.
 *   <li>Read the database state.
 *   <li><b>Optional</b>: emit a {@link DomainNotification} if {@link DomainView} succeeded.
 *   <li><b>Optional</b>: emit a {@link DomainNotification} if {@link DomainView} failed.
 * </ul>
 *
 * <p>Because {@link DomainView} leverages new Java {@code sealed} feature, for more type safety
 * this class also makes use of the same feature.
 *
 * <p><b>Design note</b>: whichever parameters can be controlled must be covered with null checks
 * and {@code final} (if possible), whichever parameters are expected to be provided by consumer
 * must be checked with the help of {@link Suspicious} methods.
 *
 * @param <VIEW> the type of the particular {@link DomainView}
 * @param <POJO> the type of the particular {@link DomainView} output element, which usually does
 *     not extend {@link UpdatableRecord}
 * @param <OUTPUT> the expected output type of the given view, typically {@link Optional} or {@link
 *     Stream} - containers which can be empty
 * @see <a href="https://rules.sonarsource.com/java/RSPEC-119/">Suppressed Sonar rule for the sake
 *     to have more readable type names</a>
 */
@SuppressWarnings("squid:S119")
// @formatter:off
public abstract sealed class DomainViewHandler<
  VIEW extends DomainView<?, ?>,
  POJO,
  OUTPUT
>
extends
        DomainHandler<VIEW, OUTPUT>
permits
  DomainViewHandler.One,
  DomainViewHandler.Many
{
// @formatter:on
  private final Class<VIEW> viewClass;
  private final Class<POJO> viewOutputClass;

  /**
   * Default constructor.
   *
   * @param viewClass this handler is intended for
   * @param viewOutputClass this handler will return
   */
  protected DomainViewHandler(final Class<VIEW> viewClass, final Class<POJO> viewOutputClass) {
    this.viewClass = throwIllegalArgumentIfNull(viewClass, "View class");
    this.viewOutputClass = throwIllegalArgumentIfNull(viewOutputClass, "View output class");
  }

  /**
   * @return specific {@link DomainView} class
   */
  public final Class<VIEW> getViewClass() {
    return viewClass;
  }

  /**
   * @return specific class of the view output
   */
  public final Class<POJO> getViewOutputClass() {
    return viewOutputClass;
  }

  /**
   * Defines business logic of this particular {@link DomainViewHandler}.
   *
   * @param view being invoked
   * @param dsl created by {@link BoundedContext} to perform database querying
   * @return database records
   * @throws Exception if any happened during invocation
   * @see <a href="https://rules.sonarsource.com/java/RSPEC-112/">Suppressed Sonar rule to allow
   *     more flexibility</a>
   */
  @SuppressWarnings("squid:S112")
  protected abstract OUTPUT run(final VIEW view, final DSLContext dsl) throws Exception;

  /**
   * @param databaseRecord to convert to the expected type
   * @return a new instance of the class we have to return
   */
  protected abstract POJO convert(final Record databaseRecord);

  /**
   * General business logic invocation to be used and exposed via {@link BoundedContext}.
   *
   * <p>The usage of {@link Try} will "hide" the exception that can be thrown by {@link
   * #run(DomainView, DSLContext)} - but not get rid of it. Actual exception along with its
   * stacktrace will be emitted by {@link BoundedContext} upon this handler invocation.
   *
   * <p>This method is package-private as it is intended to be invoked by {@link BoundedContext}
   * only.
   *
   * @param view being invoked
   * @param readWriteDsl to use for notifications
   * @param readOnlyDsl to use for the invocation
   * @param domainNotificationProducer to publish {@link DomainNotification}s with
   * @return a result of command invocation
   */
  final OUTPUT runInContext(
      final VIEW view,
      final DSLContext readWriteDsl,
      final DSLContext readOnlyDsl,
      final DomainNotificationProducer domainNotificationProducer) {
    final VIEW nonNullView = throwIllegalArgumentIfNull(view, "View");
    final DomainClient nonNullDomainClient =
        throwIllegalStateIfNull(nonNullView.domainClient(), "View's client");

    if (!canBeUsedBy(nonNullDomainClient)) {
      throw new UnauthorizedException(
          "Client '%s' is not allowed to use '%s' view"
              .formatted(nonNullDomainClient.domainRole(), getViewClass().getSimpleName()));
    }

    final DSLContext nonNullReadWriteDsl = throwIllegalStateIfNull(readWriteDsl, "Read-write DSL");
    final DSLContext nonNullReadOnlyDsl = throwIllegalStateIfNull(readOnlyDsl, "Read-only DSL");

    final DomainNotificationProducer nonNullDomainNotificationProducer =
        throwIllegalStateIfNull(domainNotificationProducer, "Notification Publisher");

    final Try<OUTPUT> output =
        Try.of(
            () ->
                throwIllegalStateIfNull(
                    run(nonNullView, nonNullReadOnlyDsl), "View handler result"));

    nonNullReadWriteDsl.transaction(
        (final Configuration trx) -> {
          output.ifSuccess(
              result -> {
                final var optionalNotification =
                    throwIllegalStateIfNull(
                        onSuccess(nonNullView, result),
                        "View handler Optional successful notification");
                optionalNotification.ifPresent(
                    notification ->
                        nonNullDomainNotificationProducer.store(trx.dsl(), notification));
              });

          output.ifFailure(
              reason -> {
                final var optionalNotification =
                    throwIllegalStateIfNull(
                        onFailure(nonNullView, reason),
                        "View handler Optional failure notification");
                optionalNotification.ifPresent(
                    notification ->
                        nonNullDomainNotificationProducer.store(trx.dsl(), notification));
              });
        });

    return output.get();
  }

  /**
   * A variant of the {@link DomainViewHandler} for {@link DomainView.One}.
   *
   * @param <ONE> the type of the particular {@link DomainView.One}
   * @param <POJO> the user-defined Java type of the records to return
   */
  // @formatter:off
  public abstract static non-sealed class One<
    ONE extends DomainView<?, ?>,
    POJO
  > extends DomainViewHandler<ONE, POJO, Optional<POJO>> {
  // @formatter:on
    protected One(final Class<ONE> viewClass, final Class<POJO> viewOutputClass) {
      super(viewClass, viewOutputClass);
    }
  }

  /**
   * A variant of the {@link DomainViewHandler} for {@link DomainView.Many}.
   *
   * @param <MANY> the type of the particular {@link DomainView.Many}
   * @param <POJO> the user-defined Java type of the records to return
   */
  // @formatter:off
  public abstract static non-sealed class Many<
    MANY extends DomainView<?, ?>,
    POJO
  > extends DomainViewHandler<MANY, POJO, List<POJO>> {
  // @formatter:on
    protected Many(final Class<MANY> viewClass, final Class<POJO> viewOutputClass) {
      super(viewClass, viewOutputClass);
    }
  }
}
