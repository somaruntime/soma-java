# 实时派工规则引擎参考应用

类型：应用开发者文档

状态：当前

Owner：real-time-dispatch-rule-engine

对 SOMA 产品规范性：否

最后审查日期：2026-07-28

本应用是独立 Java 8 consumer。它从可重放的 detached runtime snapshot/delta
开始，把两张 SOMA Table 绑定到可复用 dispatch-rule DataFlow，每个周期创建一次
one-shot Invocation，再把 detached `DispatchCommand` 交给应用按 stable key
预检和顺序提交。它不包含 MES、JDBC/CDC、重试、跨 Table transaction 或分布式
执行。

## 入口

- [Blueprint](blueprint.md)：从使用者视角理解配置、场景、规则执行和结果；
- [Design](design.md)：领域分层、DataFlow、提交、资源与失败边界；
- [Validation](validation.md)：reference differential、并行、规模和性能证据。

## 构建与运行

先把 SOMA candidate artifacts 安装到可解析的 Maven repository，再执行：

```bash
./mvnw -f soma-examples/real-time-dispatch-rule-engine/pom.xml clean package
java -cp "<application classes>:<runtime classpath>" \
  com.hgtech.soma.examples.rtd.RealTimeDispatchApplication default
```

第一个参数可以是 `default` 或外部 `.properties` 文件，后续参数使用
`key=value` 覆盖。production JAR 只内置 `default`；`correctness`、`large`、
`long-run` 和 benchmark options 位于 test resources。场景配置与测量配置互相
独立，输入 Factory 与运行时状态也不共享生命周期。

项目级 canonical Gate：

```bash
./scripts/check-real-time-dispatch-rule-engine.sh
```

该 Gate 在 evidence-local repository 中验证普通 consumer 构建、production JAR
纯度、包依赖、配置边界、replay、plain-Java reference、sequential/managed/
borrowed 一致性、budget/cancellation、application-owned commit、detached
Result 和 profile baseline。所有性能 artifact 均为本机非回归证据并保持
`claimAllowed=false`。
