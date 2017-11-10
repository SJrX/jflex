package jflextest;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/** Runs test cases in the JFlex test suite */
@Mojo(name = "run-test-suite", defaultPhase = LifecyclePhase.TEST)
public class JFlexTestsuiteMojo extends AbstractMojo {

  /** Name of the directory into which the code will be generated. */
  @Parameter(defaultValue = "src/test/cases")
  private String testDirectory = null;

  /** Whether test suite output should be verbose. */
  @Parameter(defaultValue = "false")
  private boolean verbose;

  /**
   * (Comma-separated list of) name(s) of test case(s) to run.
   *
   * <p>By default, all test cases in src/test/cases/ will be run.
   */
  @Parameter private String testCases;

  /** JFlex version under test. */
  @Parameter private String jflexTestVersion;

  /** Runs all test cases in {@link #testDirectory}. */
  public void execute() throws MojoExecutionException, MojoFailureException {
    boolean success = true;
    try {
      System.setOut(new PrintStream(System.out, true));
      List<File> files = new ArrayList<>();
      getLog().info("Testing version: " + Exec.getJFlexVersion());
      getLog().info("Test directory: " + testDirectory);
      getLog().info("Test case(s): " + (null == testCases ? "All" : testCases));

      if (testCases != null && testCases.length() > 0) {
        for (String testCase : testCases.split("\\s*,\\s*")) {
          File dir = new File(testDirectory, testCase.trim());
          if (!dir.isDirectory()) {
            throw new MojoFailureException("Test path not found: " + dir);
          }
          List<File> t = Tester.scan(dir, ".test", false);
          files.addAll(t);
        }
      }

      // if we still didn't find anything, scan the whole test path
      if (files.isEmpty()) files = Tester.scan(new File(testDirectory), ".test", true);

      Tester tester = new Tester();
      tester.verbose = verbose;
      tester.jflexTestVersion = jflexTestVersion;
      getLog().info("verbose: " + verbose);
      success = tester.runTests(files);

    } catch (Exception e) {
      throw new MojoExecutionException("Exception", e);
    }
    if (!success) {
      throw new MojoFailureException("Test(s) failed.");
    }
  }
}
