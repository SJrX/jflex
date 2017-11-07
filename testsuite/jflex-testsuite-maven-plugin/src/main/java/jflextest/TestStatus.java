package jflextest;

import com.google.common.base.Optional;

/** Result of the execution of a test. */
public class TestStatus {

  private static final Optional<Exception> NO_EXCEPTION = Optional.absent();

  final String name;
  final Status status;
  final Optional<Exception> optionalException;

  private TestStatus(String name, Status status, Optional<Exception> optionalException) {
    this.name = name;
    this.status = status;
    this.optionalException = optionalException;
  }

  public static TestStatus createTestStatusSuccess(String name) {
    return new TestStatus(name, Status.PASS, NO_EXCEPTION);
  }

  public static TestStatus createTestStatusFailure(String name, Exception e) {
    return new TestStatus(name, Status.FAIL, Optional.of(e));
  }

  public static TestStatus createTestSatusSkipped(String name) {
    return new TestStatus(name, Status.SKIP, NO_EXCEPTION);
  }

  public enum Status {
    PASS,
    FAIL,
    SKIP
  }
}
