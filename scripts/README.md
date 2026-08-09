# Stable repository commands

这里仅保留人和 CI 可以直接调用的稳定入口：

- `check.sh`：日常 correctness、generated surface、Examples 和 local package 检查；跳过百万行 profile；
- `qualify.sh`：完整 non-publishing qualification，包含 profile；
- `benchmark.sh`：在独立 JVM 中重放三个 reference application 的长期基线，以及显式选择的
  benchmark-only type-kernel 资格矩阵；
- `package-local.sh`：生成两项 artifact、sources/javadocs、SBOM、checksum、provenance 和 source bundle。

这些命令要求 `JAVA_HOME` 指向 Java 8 JDK。能力级 fixture、golden、code generation 与资格实现
位于 `tests/` 和 `build-support/`，不作为普通 library user 的入口。
