![SOMA Java](assets/soma-banner.png)

# SOMA Java

**SOMA（State-Oriented Memory Architecture）** 是面向 Java 8 的进程内编译式 Table
引擎。它让 application 使用自然 Java object、类型安全的 generated API 和 Stream-like
操作管理大规模可变状态，同时由 SOMA 负责列式存储、Key/Index、查询规划、关系计算、
受控并行、压缩和资源边界。

```text
Java application objects
    -> schema-generated typed API
        -> logical plan and optimizer
            -> specialized execution over columnar state
```

SOMA 适合调度与优化算法、仿真、实时派工等结构稳定但状态频繁变化的计算场景。它借鉴
Java Stream 的 source/intermediate/terminal、lazy pipeline 和显式并行模型，但 Table 是
SOMA 管理的 authoritative state，并额外拥有 Key、Index、mutation、GroupBy 和 Join。

## 核心能力

- package-level schema 与 Java 8 annotation processor；
- generated `Soma`、`SomaGroup`、Table、Field、Index、View 和 Editor API；
- primitive/reference chunked column storage 与 `long` domain；
- point lookup/mutation、typed filter、map、aggregate、GroupBy 和二元 Equality/Cross Join；
- sequential default、显式 `parallel()`、deterministic result 与 structured failure；
- retained/temporary memory admission、AUTO/OFF compression、`_metadata()` 与 `_explain()`。

SOMA 不提供 persistence、distributed execution、cross-Table transaction、ChildTable、ORM 或
任意业务对象映射。跨 Table 编排、外部副作用、业务补偿和普通 Java object 的线程安全仍由
application 负责。

## 快速开始

要求：Java 8 JDK 与 Maven 3.9.x。

先从当前源码构建两项 production artifact：

```sh
mvn clean install -Dmaven.install.skip=false -DskipTests
```

在一个 schema package 中声明 composition：

```java
@SomaTable(defaultCapacity = 4096)
final class Job {
    @SomaKey long jobId;
    @SomaIndex long statusCode;
    @SomaField long releaseMinute;
}
```

```java
@io.github.somaruntime.soma.SomaSchema
package example.scheduling.schema;
```

Processor 会生成同 package composition 对应的类型安全入口。Application 只使用 generated
API：

```java
SomaGroup group = Soma.createGroup();
JobTable jobs = group.jobTable();

jobs.add(new Job(101L, 0L, 10L));

long pending = jobs
        .filter(jobs.statusCode.eq(0L))
        .count();
```

完整、可运行的 schema、application service、补偿流程和关系计算请从
[三个 reference application](soma-examples/README.md) 开始。

## 构建与验证

```sh
./scripts/check.sh          # 日常完整正确性与本地交付检查，不运行百万行 profile
./scripts/qualify.sh        # 包含三个场景的百万行 profile
./scripts/package-local.sh  # 生成本地 artifact、SBOM、checksum 与 source bundle
```

当前 V1 implementation 已完成 G1–G10 本地与 non-publishing qualification；仓库尚未发布
GitHub Release、GitHub Package 或 Maven Central artifact，因此当前使用方式仍是 source build。
上述状态不等于正式发布声明或跨硬件性能 SLA。

## 仓库导航

| Path | 面向谁 | 内容 |
|---|---|---|
| [`soma-runtime/`](soma-runtime/README.md) | Library user | annotations、shared/generated API linkage 与 runtime |
| [`soma-processor/`](soma-processor/README.md) | Library user | schema compiler 与 generated source processor |
| [`soma-examples/`](soma-examples/README.md) | Library user | 三个严肃的 reference application |
| `docs/` | Library user | 用户文档入口；当前仅占位，后续独立治理 |
| [`benchmarks/`](benchmarks/README.md) | Maintainer | 三个真实场景的长期基线与 benchmark-only type-kernel 资格；不进入 production artifact |
| [`scripts/`](https://github.com/somaruntime/soma-java/tree/develop/scripts) | Maintainer / CI | 稳定的检查、资格与本地打包入口 |

项目蓝图、正式 Design、实施记录和 Conformance 属于设计与维护视角，集中在
[`project/`](https://github.com/somaruntime/soma-java/tree/develop/project)。Codex/Agent 应从
[`AGENTS.md`](https://github.com/somaruntime/soma-java/blob/develop/AGENTS.md) 开始导航；普通
library user 不需要阅读这些内部材料。

## 支持与许可

- 使用问题与支持边界：[SUPPORT.md](SUPPORT.md)
- 安全问题：[SECURITY.md](SECURITY.md)
- 行为准则：[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- 许可证：[Apache License 2.0](LICENSE)
- 品牌与第三方声明：[NOTICE](NOTICE)、[assets/README.md](assets/README.md)
