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

package io.github.suppierk.ddd.authorization;

import java.io.Serial;

/**
 * A specific {@link Exception} to be thrown if a {@link DomainClient} cannot invoke an operation.
 */
public class UnauthorizedException extends RuntimeException {
  @Serial private static final long serialVersionUID = 7996325054039085081L;

  /**
   * Constructs a new runtime exception with {@code null} as its detail message.
   *
   * <p>The cause is not initialized, and may subsequently be initialized by a call to {@link
   * #initCause}.
   */
  public UnauthorizedException() {
    super();
  }

  /**
   * Constructs a new runtime exception with the specified detail message.
   *
   * <p>The cause is not initialized, and may subsequently be initialized by a call to {@link
   * #initCause}.
   *
   * @param message the detail message (which is saved for later retrieval by the {@link
   *     #getMessage()} method).
   */
  public UnauthorizedException(String message) {
    super(message);
  }

  /**
   * Constructs a new runtime exception with the specified detail message and cause.
   *
   * <p>Note that the detail message associated with {@code cause} is <i>not</i> automatically
   * incorporated in this runtime exception's detail message.
   *
   * @param message the detail message (which is saved for later retrieval by the {@link
   *     #getMessage()} method).
   * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
   *     (A {@code null} value is permitted, and indicates that the cause is nonexistent or
   *     unknown.)
   */
  public UnauthorizedException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new runtime exception with the specified cause and a detail message of {@code
   * (cause==null ? null : cause.toString())} (which typically contains the class and detail message
   * of {@code cause}).
   *
   * <p>This constructor is useful for runtime exceptions that are little more than wrappers for
   * other throwables.
   *
   * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
   *     (A {@code null} value is permitted, and indicates that the cause is nonexistent or
   *     unknown.)
   */
  public UnauthorizedException(Throwable cause) {
    super(cause);
  }

  /**
   * @return the most appropriate HTTP status code for this exception for consumer convenience.
   * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/403">403 Forbidden</a>
   * @see <a href="https://rules.sonarsource.com/java/RSPEC-3400/">Suppressed Sonar rule about
   *     declaring a constant instead</a>
   */
  @SuppressWarnings("squid:S3400")
  public final int getStatusCode() {
    return 403;
  }
}
