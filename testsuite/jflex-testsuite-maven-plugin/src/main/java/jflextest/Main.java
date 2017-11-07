package jflextest;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import jflextest.TestStatus.Status;

public class Main {

  public static boolean verbose;
  public static String jflexTestVersion;

  public static final String version = "1.0alpha";

  private static final int THREADS = 10;

  // TODO Change number of threads
  private static ListeningExecutorService executorService =
      MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(THREADS));

  public static void showUsage(String error) {
    System.out.println("Usage: [-v] [-testpath path] <test1> <test2> <test3> ...");
    System.out.println();
    System.out.println(error);
  }

  /**
   * Scan a directory for files with specific extension
   *
   * @return a list of files
   */
  public static List<File> scan(File dir, final String extension, boolean recursive) {
    List<File> result = new ArrayList<>();

    FilenameFilter extFilter =
        new FilenameFilter() {
          public boolean accept(File f, String name) {
            return name.endsWith(extension);
          }
        };

    String[] files = dir.list(extFilter);
    if (files != null) {
      for (String file : files) {
        result.add(new File(dir, file));
      }
    }

    if (!recursive) {
      return result;
    }

    FilenameFilter dirFilter =
        new FilenameFilter() {
          public boolean accept(File f, String name) {
            return (new File(f, name)).isDirectory();
          }
        };

    String[] dirs = dir.list(dirFilter);
    if (dirs == null) {
      return result;
    }

    for (String childDir : dirs) {
      List<File> t = scan(new File(dir, childDir), extension, true);
      result.addAll(t);
    }

    return result;
  }

  /**
   * @param tests a list of File
   * @return true if all tests succeeded, false otherwise
   */
  public static boolean runTests(List<File> tests) {
    System.out.println(String.format("Running tests on %d threads", THREADS));
    ListenableFuture<List<TestStatus>> allResults =
        Futures.allAsList(Lists.transform(tests, runTestAsyncFunction));
    Futures.addCallback(allResults, displayResultCallback, directExecutor());
    ListenableFuture<Integer> passingTests =
        Futures.transform(allResults, countPassingTestsFunction, directExecutor());

    // Give some Status
    System.out.println();
    try {
      int successCount = passingTests.get();
      int totalCount = tests.size();
      System.out.println(
          "All done - "
              + successCount
              + " tests completed successfully, "
              + (totalCount - successCount)
              + " tests failed.");
      return 0 == (totalCount - successCount);
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    return false;
  }

  /** A function that executes a test asynchronously. */
  static Function<File, ListenableFuture<TestStatus>> runTestAsyncFunction =
      new Function<File, ListenableFuture<TestStatus>>() {
        @Nullable
        @Override
        public ListenableFuture<TestStatus> apply(@Nullable final File testFile) {
          return executorService.submit(
              new Callable<TestStatus>() {
                @Override
                public TestStatus call() throws Exception {
                  return runTest(testFile);
                }
              });
        }
      };

  private static TestStatus runTest(File test) {
    // set path to test
    File currentDir = new File(test.getParent());
    // trying to load
    TestLoader loader = null;
    try {
      loader = new TestLoader(new FileReader(test));

      TestCase currentTest = loader.load();
      currentTest.init(currentDir);

      // success? -> run
      if (currentTest == null) {
        throw new TestFailException("not loaded");
      }
      if (verbose) {
        System.out.println("Loaded successfully"); // - Details:\n"+currentTest);
      }
      if (currentTest.checkJavaVersion()) {
        currentTest.createScanner();
        while (currentTest.hasMoreToDo()) {
          currentTest.runNext();
        }
        return TestStatus.createTestStatusSuccess(test.toString());
      } else {
        return TestStatus.createTestSatusSkipped(test.toString());
      }
    } catch (Exception e) {
      return TestStatus.createTestStatusFailure(test.toString(), e);
    }
  }

  private static FutureCallback<List<TestStatus>> displayResultCallback =
      new FutureCallback<List<TestStatus>>() {
        @Override
        public void onSuccess(@Nullable List<TestStatus> results) {
          for (TestStatus result : results) {
            System.out.print("[" + result.status + "] Test " + result.name);
            if (result.optionalException.isPresent()) {
              Exception ex = result.optionalException.get();
              System.out.print(": " + ex.getMessage());
              System.out.print(Joiner.on("\n").join(ex.getStackTrace()));
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          System.err.println("Test Error " + t);
          t.printStackTrace(System.err);
        }
      };

  private static Function<List<TestStatus>, Integer> countPassingTestsFunction =
      new Function<List<TestStatus>, Integer>() {
        @Nullable
        @Override
        public Integer apply(@Nullable List<TestStatus> input) {
          int successfulTests = 0;
          for (TestStatus result : input) {
            if (result.status == Status.PASS || result.status == Status.SKIP) {
              successfulTests++;
            }
          }
          return successfulTests;
        }
      };

  public static void main(String[] argv) {
    System.setOut(new PrintStream(System.out, true));
    System.out.println("JFlexTest Version: " + version);
    System.out.println("Testing version: " + Exec.getJFlexVersion());

    File dir = new File("testcases"); // assume user.dir/testcases
    List<File> files = new ArrayList<File>();

    for (int i = 0; i < argv.length; i++) {

      if (argv[i].equals("-testpath")) {
        if (++i >= argv.length) {
          showUsage("missing path argument");
          return;
        }
        dir = new File(argv[i]);
        // Quit if dir is not valid
        if (!dir.isDirectory()) {
          showUsage(dir + " - test path not found");
          return;
        }

        continue;
      }

      if (argv[i].equals("-v")) {
        verbose = true;
        continue;
      }

      List<File> t = scan(new File(dir, argv[i]), ".test", false);
      files.addAll(t);
    }

    // if we still didn't find anything, scan the whole test path
    if (files.isEmpty()) {
      files = scan(dir, ".test", true);
    }

    runTests(files);
  }
}
