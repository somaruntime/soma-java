# 个体生态仿真参考应用

类型：应用开发者文档

状态：当前

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

最后审查日期：2026-07-23

本应用是独立 Java 8 Maven consumer。它拥有 grasser–grass 模型、system
顺序、配置、确定性随机、验证和性能 evidence；SOMA 的产品语义仍由根级
Design 拥有。它只依赖 `soma-annotations`、`soma-runtime-core` 和
compile-time `soma-processor`，不依赖 root reactor、`soma-testkit`、
旧 examples、Artemis-odb 或 internal package。

## 入口

- [Blueprint](blueprint.md)：从使用者视角理解配置、状态和 tick journey；
- [Design](design.md)：领域系统、runtime projection、确定性与失败边界；
- [Validation](validation.md)：AoS oracle、长期运行和多 fork 证据。

## 构建与运行

先把 SOMA candidate artifacts 安装到可解析的 Maven repository，再执行：

```bash
./mvnw -f soma-examples/grassing-individual-simulation/pom.xml clean package
java -cp "<application classes>:<runtime classpath>" \
  com.hgtech.soma.examples.grassing.SimulationApplication default
```

第一个参数可以是 `correctness`、`default`、`large`、`long-run`，也可以是
`.properties` 文件；后续参数使用 `key=value` 显式覆盖。程序输出最终生效配置、
config/input/result 三种 checksum 和最终生态摘要。

项目级 canonical Gate：

```bash
./scripts/check-grassing-simulation.sh
```

该 Gate 在 evidence-local Maven repository 中构建普通 consumer，执行四个配置、
逐 tick AoS oracle、Index/lifecycle 负路径、长期 invariant 和三个独立 JVM fork。
所有性能 artifact 默认 `claimAllowed=false`。
