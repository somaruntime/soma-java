# 工业动态调度参考应用

类型：应用开发者文档

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-24

本应用是一个独立 Java 8 consumer：它只依赖 `soma-annotations`、
`soma-runtime-core` 和 compile-time `soma-processor`，不依赖 root reactor、
`soma-testkit`、旧 examples 或 internal package。

## 入口

- [Blueprint](blueprint.md)：从使用者视角理解配置、运行和结果；
- [Design](design.md)：领域、runtime projection、事件与失败边界；
- [Validation](validation.md)：oracle、long-run 和多 fork 证据。

## 构建与运行

先把 SOMA candidate artifacts 安装到可解析的 Maven repository，再执行：

```bash
./mvnw -f soma-examples/industrial-dynamic-scheduler/pom.xml clean package
java -cp "<application classes>:<runtime classpath>" \
  com.hgtech.soma.examples.scheduler.application.SchedulerApplication default
```

生产资源只提供 `default` problem profile；第一个参数也可以是外部
`.properties` 文件，后续参数使用 `key=value` 显式覆盖。程序输出最终生效配置、
generation provenance、config/input/result 三种 checksum，以及 generation、
preparation、solve 各自耗时。input checksum 只表达 Problem 领域事实，不把
generator version、seed 或集合排列顺序当成语义身份。

`correctness`、`large`、`long-run`、fixture、oracle、verification 和 benchmark
全部位于 test source-set，不进入生产 JAR。Problem generation 配置与 benchmark
measurement 配置彼此独立。

项目级 canonical Gate：

```bash
./scripts/check-industrial-scheduler.sh
```

该 Gate 在 evidence-local Maven repository 中构建普通 consumer，检查 production
JAR 和 package DAG，执行四个 problem profile、手算 oracle、完整 Result claim
validator、projection/lifecycle 负路径和默认性能回归。default、large、long-run
的专项性能入口分别为 Fast、Scale、Soak，Full 组合三个 profile 与 simulation
的对应 profile。每个普通性能 Gate 使用三个独立 JVM fork，并分别保护 hot solve
与 `prepare + solve + close` canonical path。所有性能 artifact 默认
`claimAllowed=false`；
同环境下由统一 comparator 判定 `passed/failed`，环境不同且 artifact 合法时明确
返回 `not-applicable`。
