# Schema 与生成 API 设计

类型：Design

状态：候选

Owner：SOMA schema 与 generated contract

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：annotation 语义、schema normalization/hash、编译期诊断和 generated facade 语义

非事实范围：runtime 存储算法、具体 generator 类结构和 measured performance

最后审查日期：2026-07-20

## 1. Schema vocabulary

- `@SomaSchema` 定义一个生成单元和 namespace；
- `@SomaValue` 定义 compiler-owned immutable value type，具有稳定 value equality/hash 和 leaf flattening；
- `@SomaTable` 定义 row schema 及 detached single-row materialization shape；
- `@SomaField`、`@SomaIgnore`、`@SomaSemantic` 描述受控字段语义；
- `@SomaOptional` 与 `@SomaDefault` 明确 absence/default，禁止用任意 sentinel 暗示缺失；
- `@SomaChild` 定义 parent-owned `List<R>` 或 `Map<K,R>` child；
- `@SomaKey` 定义 primary identity；`@SomaUnique`、`@SomaIndex` 定义 secondary exact access。

Schema 不提供 range index、maintained order 或 application priority queue。排序策略由调用点显式给出。

## 2. 编译期规则

Full JDK 8 javac 是 V1 compiler authority。`@SomaValue` 需要 javac integration 提供 immutable/public-final-field/value semantics，JSR 269 processor 负责发现、验证、规范化、hash 和生成；两者必须以版本化 identity 协同，不能只靠新 JDK 的 `--release 8` 模拟。

编译期必须拒绝：

- 不受支持的 field/type/modifier/annotation 组合；
- key/unique/index 的非法目标、重复名称或冲突定义；
- child ownership shape、optional/default 或 value nesting 的歧义；
- 生成名称冲突、保留标识符冲突和无法稳定排序的 schema；
- 超过明确 codegen 资源上限的 schema；
- generated/compiler/runtime protocol identity 不匹配。

诊断必须稳定、可定位、bounded，不能依赖普通 stdout 日志。

## 3. Normalization 与 schema hash

同一语义 schema 必须得到确定的 normalized model、生成顺序和 schema hash。Normalization 使用明确的 Unicode code-point order，不依赖文件系统、locale、reflection iteration 或 JVM hash iteration。

Schema hash 表达 schema contract identity；runtime plan hash 表达运行时策略 identity，两者不得混用。对 hash 输入或生成 manifest 的非兼容变化必须经过 compatibility review。

## 4. Generated facade

生成 API 至少表达：

- schema-specific table create 与 runtime plan binding；
- typed batch/import；
- keyed fetch/contains/mutate/delete，或 dense packed access；
- default scan、exact selector source、filter、dynamic sort 和 terminal；
- typed mutation terminal、result/stats；
- typed Column Pipeline、primitive ColumnView 和 direct column access；
- parent-owned child facade；
- detached row/aggregate materialization与预算；
- lifecycle、structured errors 和 compatibility identity。

生成 public signature 不暴露 runtime bucket、RowSlot、raw owner token、primitive backing array 或 internal protocol type。

## 5. Row 与 callback 语义

- materializing terminal 返回 detached `@SomaTable` object；它不是 live view；
- pipeline callback 收到 callback-scoped row cursor/mutator，不能逃逸、缓存或跨 stage 使用；
- Row Pipeline 和 mutation builder 是 one-shot；消费后再次调用必须产生 typed lifecycle error；
- `IndexSnapshot` 是显式复制的 public Index result，只复制数值序列并记录 source table / structural epoch；它不是 stable identity 或 row snapshot；
- caller只在一个同步只读Index消费批次中立即使用，来源Table任意mutation/lifecycle变化后视为失效；`requireCurrent`只作为可选边界防御，不进入强制hot path；
- internal candidate scratch 统一称为 `IndexBuffer`，不进入 application data model；
- callback failure 必须遵守 mutation atomicity，不允许 partially committed row set。

## 6. API 演进

Handwritten public、generated public 和 generated-runtime protocol 是不同兼容面。任何 public/generated 名称或语义变化必须更新 manifest/golden/external-consumer evidence；internal implementation 可以在不改变这些 surface 的前提下演进。
