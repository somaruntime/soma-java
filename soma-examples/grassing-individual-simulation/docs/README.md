# 个体生态仿真参考应用

类型：应用开发者文档

状态：当前

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

最后审查日期：2026-07-28

本应用是独立 Java 8 Maven consumer。它以
`Config -> Scenario Factory -> Scenario -> Simulator/Session -> Result`
作为唯一 production journey，并拥有 grasser–grass 模型、system 顺序、配置、
确定性随机、验证和性能 evidence；SOMA 的产品语义仍由根级 Design 拥有。它只
依赖 `soma-annotations`、`soma-runtime-core`、生成 companion 所需的
`soma-dataflow` 和 compile-time `soma-processor`，不依赖 root reactor、
`soma-testkit`、旧 examples、Artemis-odb 或 SOMA internal package。业务运行
仍使用 direct Access/Transformation，不为了展示产品能力强行建立 reusable
DataFlow。

## 入口

- [Blueprint](blueprint.md)：从使用者视角理解配置、场景、会话和 tick journey；
- [Design](design.md)：应用分层、领域系统、runtime projection、生命周期与失败边界；
- [Validation](validation.md)：source-set、架构 Gate、AoS oracle、长期运行和多 fork 证据。

## 构建与运行

先把 SOMA candidate artifacts 安装到可解析的 Maven repository，再执行：

```bash
./mvnw -f soma-examples/grassing-individual-simulation/pom.xml clean package
java -cp "<application classes>:<runtime classpath>" \
  com.hgtech.soma.examples.grassing.SimulationApplication default
```

第一个参数可以是 `default`，也可以是 `.properties` 文件；后续参数使用
`key=value` 显式覆盖。production JAR 只内置 `default`，普通使用者应通过显式
`.properties` 文件改变规模或初始状态；`correctness`、`large` 和 `long-run`
是专项 Gate 的 test-only profiles。程序输出最终生效配置、config/input/result
三种 checksum 和最终生态摘要。

项目级 canonical Gate：

```bash
./scripts/check-grassing-simulation.sh
```

该 Gate 在 evidence-local Maven repository 中构建普通 consumer，执行四个配置、
逐 tick AoS oracle、Index/lifecycle 负路径、长期 invariant 和三个独立 JVM fork；
同时验证 production JAR purity、package DAG、canonical contract、retired identity、
clean/repeat manifest、generated/schema reproducibility 与 Java major 52。所有性能
artifact 默认 `claimAllowed=false`。同环境下，版本化 application baseline 由
统一 comparator 判定 `passed/failed`；环境不同且 artifact 合法时明确返回
`not-applicable`。

Default、large、long-run 的专项性能入口分别为 Fast、Scale、Soak，Full 组合三个
profile 与 scheduler、RTD 的对应 profile。每个普通性能 Gate 使用三个独立 JVM
fork。
