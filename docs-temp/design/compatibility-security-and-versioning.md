# 兼容性、安全与版本设计

类型：Design

状态：候选

Owner：SOMA compatibility、security 与 release identity

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：兼容面、协议 identity、输入信任边界、版本与产品身份

非事实范围：具体 release 进度、账户/签名配置和某次安全扫描结果

最后审查日期：2026-07-19

## 1. 兼容面

以下 surface 必须分别治理：

| Surface | 例子 | 变更要求 |
|---|---|---|
| handwritten public | annotation、runtime plan、budget、errors、stats/results | public API review 与 consumer evidence |
| generated public | schema-specific table/batch/pipeline/view/child API | manifest、golden 与 external compile/run |
| generated-runtime protocol | generated code 与 runtime-core binding | identity check；不兼容变化升级 protocol |
| schema contract | annotation semantics、normalization、hash input | schema/hash compatibility review |
| compiler integration | javac lowering 与 processor coordination | compiler identity 与 full JDK 8 evidence |
| materialization | detached shape、budget、estimator | output/budget compatibility review |
| internal | processor/runtime implementation | 在不泄漏时可自由演进 |
| evidence artifact | report/benchmark/package schema | versioned parser/validator |

“Java 源码能编译”不能替代 generated/runtime/schema compatibility。

## 2. Identity

Generated artifact、runtime 和 runtime plan 在 create boundary 互相验证。当前 V3 identity 为：

- generated protocol：`soma-generated-runtime-v3`；
- runtime compatibility：`soma-runtime-java8-v3`；
- runtime plan protocol：`soma-runtime-plan-v3`；
- dense storage algorithm：`dense-soa-v1`；
- materialization estimator：`soma-materialization-estimator-v1`。

Schema hash 与 runtime plan hash 分离。Mismatch 在 aggregate 发布前以 typed compatibility failure 拒绝，不能降级到反射、scan 或 best-effort adapter。

## 3. Java 与产品身份

- V1 language/runtime boundary 是 Java 8；full JDK 8 javac 是 compiler authority；
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

## 5. Release 边界

功能/性能 evidence 与 public release readiness 分开。只有 package metadata、license/notice、SCM/ownership、support matrix、签名/provenance、security scan 和 external consumer 等发布 Gate 全部形成证据后，才可以声明 public RC/release readiness。

本 Design 规定必须满足的身份与边界；当前 readiness 由相应 Report 陈述。
