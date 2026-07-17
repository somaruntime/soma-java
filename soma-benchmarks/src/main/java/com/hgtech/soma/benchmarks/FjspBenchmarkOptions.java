package com.hgtech.soma.benchmarks;

import java.io.File;

/** FJSP diagnostic benchmark 的命令行配置。 */
final class FjspBenchmarkOptions {
  File output = new File("target/fjsp-100k-benchmark.jsonl");
  int warmup = 1;
  int measurements = 3;
  long seed = 0x534f4d41L;

  static FjspBenchmarkOptions parse(String[] args) {
    FjspBenchmarkOptions options = new FjspBenchmarkOptions();
    for (int index = 0; index < args.length; index++) {
      String option = args[index];
      if ("--output".equals(option)) {
        options.output = new File(value(args, ++index, option));
      } else if ("--warmup".equals(option)) {
        options.warmup = integer(args, ++index, option);
      } else if ("--measurements".equals(option)) {
        options.measurements = integer(args, ++index, option);
      } else if ("--seed".equals(option)) {
        options.seed = Long.parseLong(value(args, ++index, option));
      } else {
        throw new IllegalArgumentException("unknown option: " + option);
      }
    }
    if (options.warmup < 0 || options.measurements <= 0) {
      throw new IllegalArgumentException("invalid warmup/measurement count");
    }
    return options;
  }

  private static String value(String[] args, int index, String option) {
    if (index >= args.length) {
      throw new IllegalArgumentException("missing value for " + option);
    }
    return args[index];
  }

  private static int integer(String[] args, int index, String option) {
    return Integer.parseInt(value(args, index, option));
  }
}
