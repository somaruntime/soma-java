# Stable repository commands

这里仅保留人和 CI 可以直接调用的稳定入口：

- `check.sh`：日常 correctness、generated/cumulative consumer与三个Examples smoke；不运行
  Benchmark、local package、packaged consumer或supply-chain资格；
- `qualify.sh`：完整 non-publishing qualification，包含clean/full regeneration、profile、
  local package、packaged consumer和supply-chain；
- `benchmark.sh`：在独立 JVM 中重放三个 reference application 的长期基线，以及显式选择的
  benchmark-only type-kernel 资格矩阵；
- `package-local.sh`：生成两项 artifact、sources/javadocs、SBOM、checksum、provenance 和 source bundle。

这些命令要求 `JAVA_HOME` 指向 Java 8 JDK。独立运行`benchmark.sh`或`package-local.sh`
会自行构建当前候选；`qualify.sh`只通过内部BuildSession manifest复用已证明同源的产物，
不支持裸`REUSE_BUILD=1`绕过。能力级fixture、golden、code generation与资格实现位于
`tests/`和`build-support/`，不作为普通library user的入口。
