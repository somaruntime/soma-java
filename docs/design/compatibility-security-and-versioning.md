# 兼容性、安全与版本设计

类型：Design

状态：正式

Owner：SOMA compatibility、security 与 release identity

设计层次：`Q` 横切质量

主要关注点：兼容面、信任边界、协议/产品身份与版本演进

上位设计：[设计宪法](soma-java-design-constitution.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：兼容面、协议 identity、输入信任边界、版本与产品身份

非事实范围：具体 release 进度、账户/签名配置和某次安全扫描结果

最后审查日期：2026-07-23

本横切 Owner 以“可消费身份如何安全演进”为共同边界：compatibility 分类决定什么可以改变，security 约束输入和协议信任，version/identity 让双方可验证。具体发布进度、账户和签名状态仍由 Engineering 与 Report 拥有。

## 1. 兼容面

以下 surface 必须分别治理：

| Surface | 例子 | 变更要求 |
|---|---|---|
| handwritten public | annotation、runtime plan、budget、errors、stats/results | public API review 与 consumer evidence |
| generated public | schema-specific Table/Batch/Scan/Traversal/View/child API | manifest、golden 与 external compile/run |
| generated-runtime protocol | generated code 与 runtime-core binding | identity check；不兼容变化升级 protocol |
| schema contract | annotation semantics、normalization、hash input | schema/hash compatibility review |
| compiler integration | javac lowering 与 processor coordination | compiler identity 与 full JDK 8 evidence |
| materialization | detached shape、budget、estimator | output/budget compatibility review |
| internal | processor/runtime implementation | 在不泄漏时可自由演进 |
| evidence artifact | report/benchmark/package schema | versioned parser/validator |

“Java 源码能编译”不能替代 generated/runtime/schema compatibility。

当前精确 signature、annotation element、generated method、error code、plan field 和 artifact schema 由代码与[可执行契约地图](../implementation-map/executable-contract-map.md)登记；本 Design 拥有这些 surface 的分类和允许怎样演进。

### 1.1 Change classification

- breaking：移除/重命名 public shape、改变已有成功/失败/ordering/ownership/materialization 语义、改变 schema/hash/protocol meaning，或让旧 generated artifact 与新 runtime 静默产生不同结果；
- additive：增加不改变既有调用解析和行为的新 surface，并补齐 compile/run evidence；
- behavior-preserving internal：不改变 public/generated/schema/protocol observable semantics 的实现重构；
- evidence-only：只修正测量、报告或验证说明，不改变产品能力。

Pre-1.0 允许有意的 breaking change，但不允许无记录漂移。Breaking change 必须有明确 Owner 决定、migration/重新生成要求、identity/version 变化和 external consumer 证据。已经发布的 public surface若需要移除，应先提供可迁移的 replacement 与 deprecation boundary；内部 package 不因此成为稳定 SPI。

## 2. Identity

Generated artifact、runtime 和 runtime plan 在 create boundary 互相验证。当前 V4 identity 为：

- generated protocol：`soma-generated-runtime-v4`；
- runtime compatibility：`soma-runtime-java8-v4`；
- runtime plan protocol：`soma-runtime-plan-v3`；
- dense storage algorithm：`dense-soa-v1`；
- materialization estimator：`soma-materialization-estimator-v1`。

Schema hash 与 runtime plan hash 分离。Mismatch 在 aggregate 发布前以 typed compatibility failure 拒绝，不能降级到反射、scan 或 best-effort adapter。

## 3. Java 与产品身份

- V1 language/runtime boundary 是 Java 8；当前实现与验真只以 Azul Zulu full JDK 8 javac/runtime 为 authority；Corretto 和其他 JDK distribution 不属于当前验真或目标支持范围；
- 组织与发布主体为 HGTECH，产品品牌为 SOMA；
- Maven `groupId` 和 Java package root 为 `com.hgtech.soma`；
- artifact 保持 `soma-*`；
- HGTECH 只进入真实组织、SCM、POM、publishing 和 provenance 边界，不成为 SOMA annotation、generated type、error 或 schema 概念前缀。

Version number、protocol identity、schema hash 和 artifact coordinates 是不同 identity，不能互相替代。

## 4. Security 与资源边界

Processor 和 runtime 将 schema/source、runtime plan、batch values、keys、callback、materialization graph 和 allocation provider 视为需验证的输入边界：

- generated names/path 必须防止碰撞、escaping 和非确定输出；
- diagnostics 不泄漏 local path、credential、raw handle、full row/table 或 arbitrary payload string；
- size、capacity、depth、count 和 byte arithmetic 必须 overflow-safe；
- schema/codegen、runtime storage、scratch 和 materialization 都受明确资源上限或 plan 约束；
- hash collision 不得破坏 full equality、unique 或 key correctness；
- callback/provider 不能借 reentrancy 观察或修改中间状态；
- runtime 不执行隐藏网络、文件 I/O、全局 logger 或动态代码加载。

Security hardening 不能通过降低 public correctness 或静默丢弃数据实现。

### 4.1 Trust boundary

SOMA 是进程内 library，不是 sandbox。它保护 schema/compiler/runtime integrity、resource boundary、deterministic output、diagnostic privacy 和 artifact supply chain；application仍负责 authentication、authorization、tenant isolation、domain validation、sensitive-data policy 和不受信任 callback 的隔离。

关键 protected assets 包括：generated source/class 与 schema identity、packed live facts、locator/index/ownership consistency、resource budget、error/diagnostic context 和发布 artifact/provenance。Hash 只能用于定位，collision 后必须执行 full equality；不能把非加密 hash 误写成 adversarial security guarantee。

Dependency、plugin、wrapper、build input 和 package metadata 必须可审计。新增第三方 runtime dependency、动态代码加载、隐式网络/文件访问或扩大 compiler privilege 都属于 Design/Security review surface。

## 5. Release 边界

功能/性能 evidence 与 public release readiness 分开。只有 package metadata、license/notice、SCM/ownership、support matrix、签名/provenance、security scan 和 external consumer 等发布 Gate 全部形成证据后，才可以声明 public RC/release readiness。

本 Design 规定必须满足的身份与边界；当前 readiness 由相应 Report 陈述。

Version label、Maven artifact version、schema user version、schema hash、generated/runtime protocol 和 runtime plan hash 分别治理。Snapshot/RC/release 不得靠改名掩盖未满足 Gate；同一已发布坐标不可变。

Release 必须可追溯到 clean immutable commit、可复现 source/binary/javadoc/checksum 和 provenance。发现错误 artifact 或安全问题时，维护者应能够停止分发、标记受影响版本、发布修复/替代并保留审计记录；rollback/withdrawal 不得静默复用原坐标内容。
