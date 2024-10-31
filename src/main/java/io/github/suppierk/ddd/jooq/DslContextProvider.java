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

package io.github.suppierk.ddd.jooq;

import io.github.suppierk.ddd.cqrs.DomainMessage;
import java.io.Serializable;
import java.time.temporal.Temporal;
import java.util.function.Function;
import org.jooq.DSLContext;

/**
 * Allows for flexibility to define {@link DSLContext}s to be used.
 *
 * <p>Extends {@link Function} to give the ability to decide which {@link DSLContext} to use based
 * on the incoming interaction, where some of the usage examples might be to select a different
 * database based on the time of the notification or certain client properties, such as client's
 * country.
 */
@FunctionalInterface
// @formatter:off
public interface DslContextProvider extends Function<
  DomainMessage<
      ? extends Serializable,
      ? extends Temporal
    >,
  DSLContext
> {
// @formatter:on

  /**
   * Similar to {@link Function#identity()}.
   *
   * @param dslContext to create {@link DslContextProvider} with
   * @return a new instance of {@link DslContextProvider} which simply returns provided {@link
   *     DSLContext}
   */
  static DslContextProvider dslContextIdentity(DSLContext dslContext) {
    if (dslContext == null) {
      throw new IllegalArgumentException("DSLContext is null");
    }

    return new DslContextProvider() {
      private final DSLContext context = dslContext;

      @Override
      public DSLContext apply(
          DomainMessage<? extends Serializable, ? extends Temporal> domainMessage) {
        return context;
      }
    };
  }
}
