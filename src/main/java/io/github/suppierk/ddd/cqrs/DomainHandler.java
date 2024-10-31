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
import java.util.Optional;

/**
 * Defines some of the common functionalities defined for handlers.
 *
 * @param <OPERATION> supported by the current handler
 * @param <OUTPUT> of the handler operation
 * @see <a href="https://rules.sonarsource.com/java/RSPEC-119/">Suppressed Sonar rule for the sake
 *     to have more readable type names</a>
 */
@SuppressWarnings("squid:S119")
abstract sealed class DomainHandler<OPERATION, OUTPUT> extends Suspicious
    permits DomainCommandHandler, DomainQueryHandler, DomainViewHandler {
  /**
   * @param domainClient invoking the command
   * @return {@code true} if the client can invoke current handler, {@code false} otherwise
   */
  protected boolean canBeUsedBy(final DomainClient domainClient) {
    return true;
  }

  /**
   * Defines a {@link DomainNotification} to deliver in case when this {@link DomainCommand} will
   * succeed.
   *
   * @param operation being invoked
   * @param output created after {@link DomainCommand} invocation
   * @return an {@link Optional} {@link DomainNotification} to deliver via {@link
   *     DomainNotificationProducer}
   * @see <a href="https://rules.sonarsource.com/java/RSPEC-1452/">Suppressed Sonar rule to denote
   *     that it doesn't matter much which exact types notification uses</a>
   */
  @SuppressWarnings("squid:S1452")
  protected Optional<DomainNotification<?, ?>> onSuccess(
      final OPERATION operation, final OUTPUT output) {
    return Optional.empty();
  }

  /**
   * Defines a {@link DomainNotification} to deliver in case when this {@link DomainCommand} will
   * fail.
   *
   * @param operation being invoked
   * @param cause is an exception that happened during processing
   * @return an {@link Optional} {@link DomainNotification} to deliver via {@link
   *     DomainNotificationProducer}
   * @see <a href="https://rules.sonarsource.com/java/RSPEC-1452/">Suppressed Sonar rule to denote
   *     that it doesn't matter much which exact types notification uses</a>
   */
  @SuppressWarnings("squid:S1452")
  protected Optional<DomainNotification<?, ?>> onFailure(
      final OPERATION operation, final Throwable cause) {
    return Optional.empty();
  }
}
