package com.hgtech.soma.examples.rtd.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** 定位并严格解析 dispatch 配置；未知或缺失字段都拒绝。 */
public final class DispatchConfigLoader {
  private static final Set<String> KEYS = Collections.unmodifiableSet(
      new HashSet<String>(Arrays.asList(
          "config.version", "generator.version", "scenario.seed",
          "work.initial", "work.arrivals.per.cycle",
          "resource.count", "capability.count",
          "dispatch.cycles", "dispatch.cycle.minutes",
          "execution.workers",
          "execution.parallel.minimum.cardinality",
          "budget.maximum.output.elements",
          "budget.maximum.tasks")));

  public DispatchConfig load(String selector, String... overrides)
      throws IOException {
    if (selector == null || selector.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "config selector must not be empty");
    }
    Properties properties = new Properties();
    InputStream input = open(selector.trim());
    try {
      properties.load(input);
    } finally {
      input.close();
    }
    TreeMap<String, String> effective =
        new TreeMap<String, String>();
    for (String key : properties.stringPropertyNames()) {
      effective.put(key, properties.getProperty(key).trim());
    }
    if (overrides != null) {
      for (String override : overrides) {
        int separator =
            override == null ? -1 : override.indexOf('=');
        if (separator <= 0 || separator == override.length() - 1) {
          throw new IllegalArgumentException(
              "override must use key=value: " + override);
        }
        effective.put(
            override.substring(0, separator).trim(),
            override.substring(separator + 1).trim());
      }
    }
    if (!effective.keySet().equals(KEYS)) {
      Set<String> missing = new HashSet<String>(KEYS);
      missing.removeAll(effective.keySet());
      Set<String> unknown =
          new HashSet<String>(effective.keySet());
      unknown.removeAll(KEYS);
      throw new IllegalArgumentException(
          "config keys mismatch; missing=" + missing
              + ", unknown=" + unknown);
    }
    return new DispatchConfig(effective);
  }

  private static InputStream open(String selector) throws IOException {
    File file = new File(selector);
    if (file.isFile()) return new FileInputStream(file);
    String resource = selector.endsWith(".properties")
        ? (selector.startsWith("config/")
            ? selector : "config/" + selector)
        : "config/" + selector + ".properties";
    InputStream input = DispatchConfigLoader.class.getClassLoader()
        .getResourceAsStream(resource);
    if (input == null) {
      throw new IOException(
          "config not found as file or resource: " + selector);
    }
    return input;
  }
}
