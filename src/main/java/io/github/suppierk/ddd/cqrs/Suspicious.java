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

/**
 * Defines internal sealed utility for verifying user inputs.
 *
 * <p>Unfortunately, annotations are not a saving grace when it comes to {@code null}s - this class
 * is used virtually everywhere to ensure that user inputs are always blocked when they attempt to
 * use {@code null}.
 */
abstract sealed class Suspicious permits BoundedContext, DomainHandler {
  /**
   * This method must be used whenever we deal with properties of classes.
   *
   * @param value which must not be {@code null}
   * @param whatMustNotBeNull is the parameter name
   * @param <T> is the type of the value
   * @return value if it was not {@code null}
   * @throws IllegalStateException when the value is {@code null}
   */
  protected final <T> T throwIllegalStateIfNull(T value, String whatMustNotBeNull)
      throws IllegalStateException {
    if (value == null) {
      throw new IllegalStateException("%s cannot be null".formatted(whatMustNotBeNull));
    }

    return value;
  }

  /**
   * This method must be used whenever we deal with method arguments only. When we need to check
   * method argument properties use {@link #throwIllegalStateIfNull(Object, String)} instead.
   *
   * @param value which must not be {@code null}
   * @param whatMustNotBeNull is the parameter name
   * @param <T> is the type of the value
   * @return value if it was not {@code null}
   * @throws IllegalArgumentException when the value is {@code null}
   */
  protected final <T> T throwIllegalArgumentIfNull(T value, String whatMustNotBeNull)
      throws IllegalArgumentException {
    if (value == null) {
      throw new IllegalArgumentException("%s cannot be null".formatted(whatMustNotBeNull));
    }

    return value;
  }

  /**
   * This method must be used whenever we cannot provide services because expected resource was
   * missing.
   *
   * @param value which must not be {@code null}
   * @param whatMustNotBeNull is the parameter name
   * @param <T> is the type of the value
   * @return value if it was not {@code null}
   * @throws UnsupportedOperationException when the value is {@code null}
   */
  protected final <T> T throwUnsupportedOperationIfNull(T value, String whatMustNotBeNull)
      throws UnsupportedOperationException {
    if (value == null) {
      throw new UnsupportedOperationException("%s is null".formatted(whatMustNotBeNull));
    }

    return value;
  }
}
