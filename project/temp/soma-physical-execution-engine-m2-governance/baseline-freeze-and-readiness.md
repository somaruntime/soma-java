# SOMA Physical Execution Engine M2 Baseline Freeze与实施准入审查

类型：Temporary Governance / Candidate Baseline Freeze / Readiness Review

状态：`PASS / CANDIDATE_BASELINE_FROZEN / READY_FOR_FORMAL_PROMOTION_REVIEW / IMPLEMENTATION_AUTHORIZATION_NOT_GRANTED`

日期：2026-08-12

Owner：M2 Candidate范围、冻结指纹、设计完备性、实施准备与授权边界

## 1. 审查结论

本次设计阶段通过：

```text
Current-state audit             PASS
Candidate Design completeness  PASS
Bounded feasibility            PASS
Overdesign review              PASS
Implementation plan            READY
Candidate baseline             FROZEN
Formal Design promotion        NOT_PERFORMED
Implementation authorization   NOT_GRANTED
Production implementation      NOT_STARTED
Release/publication             NOT_AUTHORIZED
```

没有剩余P0/P1设计裁决。下一步不是继续扩张候选模型，而是由Product Owner审核本冻结基线；审核通过后
另行完成正式Design晋升、Conformance readiness与implementation authorization。

## 2. 审查输入

Formal baseline：

| Owner / Plan | SHA-256 |
|---|---|
| `project/design/planning-and-optimization.md` | `d5517c05f0efb0743628e3a418d9295fe79e4f8c58b7b8240b11b30e6b99aa4a` |
| `project/design/execution-and-concurrency.md` | `303d8483830abf0ccd28476c4671197c1f788fd7b632ad3e8bf9504519f75bed` |
| `project/design/implementation-architecture.md` | `f180f217956716e817def2dda795df6694e52b6f55043262191903ca1cf0ffb6` |
| `project/design/core-abstractions-and-narratives.md` | `4e6cf90b5244ecaae66dce8d83c876719a664c0789563741a886deaa8b883d42` |
| `project/engineering/canonical-ir-execution-engine-implementation-plan.md` | `eecf4e03d0da4bf131243f3426b8013a76fa7c380ffbdd289ee7ce930bfaa93d` |
| `project/conformance/v1-vectorized-physical-pipeline-expansion-governance.md` | `c93dd13825b3bba68dd8fd3921ce2fb1464a83380bc199088a25e0a4b7ebcbd5` |

Executable source baseline：

```text
branch: develop
source HEAD: d106484161d70f8d3f5f344fe06c61308c1bf400
source worktree at start: clean
M1 active slice: NONE
Vector VP1-VP3 active slice: NONE
```

本审查不把已完成M1/VP1-VP3重新解释为active migration。

## 3. Candidate baseline fingerprint

冻结集合：

| Candidate document | SHA-256 |
|---|---|
| `README.md` | `75af3f02d4e140531f67ec9b499b4cdf877744ec0f3e4355c8c3bae8cfcea47b` |
| `current-state-audit.md` | `0d527209682a2f9ca9affae557dceed74835701f18daf75031ac741c7c76fa39` |
| `design.md` | `383d08751354ffb7cf0f701cbc47a8eec91619a98c13c1b96e1e41dd6c00dac5` |
| `feasibility-validation.md` | `de09706f1f3753b51e09a8237529b09dd9c708a111a8f8746439e9e6f7be8169` |
| `implementation-plan.md` | `3214896d000b8af26d266ddbdffed9416d3144b102d0aef041379abc271c45ed` |

按上述顺序对标准`shasum -a 256`输出再次取SHA-256，Candidate Baseline ID为：

```text
soma-physical-execution-engine-m2-candidate:4a89960258ab3689a788d3e22b0a117587f4268bd92fc23841f973ea0bd942f7
```

该fingerprint冻结规范性Candidate文本，不冻结private class name、threshold、partition count、hash
coefficient、codec detail、exact benchmark number或偶然test shape。任何改变核心抽象、operation mapping、
resource、Reference、parallel、stop rule或slice exit的修改都必须重新审核并更新fingerprint。

## 4. 设计完备性审查

| Check | Result | Evidence |
|---|---|---|
| 目标与必要性 | PASS | 从family-local physical facts升层，未伪称缺少执行引擎 |
| Unique Owner | PASS | Planning decision、Execution actual state、Storage truth、Mutation publication分责明确 |
| 核心模型 | PASS | Pipeline、Segment、Breaker、Kernel、Frame、Morsel均有定义、lifecycle与边界 |
| Operation coverage | PASS | Table、Field、Mapped、Primitive、Group、Relation、Selection和Point边界完整 |
| Reference | PASS | 直接解释Bound、独立state、非production fallback |
| Resource | PASS | final plan一次估算、admission-before-state、whole-operation peak |
| Parallel | PASS | Morsel复用唯一scheduler，canonical merge与quiescence明确 |
| Failure/order/numeric | PASS | 继承正式Design且列出物理不变量 |
| Migration closure | PASS | P1-P6纵向slice、无长期bridge/双decision |
| Performance protection | PASS | fixed-host A/B、profile、complexity/allocation与sub-ms守卫 |
| Stop rules | PASS | semantic、dependency、DAG、Reference、resource、tradeoff均显式阻断 |
| Release boundary | PASS | publication仍未授权 |

## 5. 过度设计审查

已删除或明确排除：

- general operator DAG；
- universal Batch/Vector/Tuple；
- public/internal plugin SPI；
- 第二scheduler/executor；
- runtime codegen与Java Vector API；
- dynamic resource lease与spill；
- Point operation统一化；
- 所有组合的预建kernel hierarchy；
- SOMA Engine/Workflow作为本专题input；
- 一次性big-bang executor rewrite。

保留的六个概念均映射到current executable fact并承担独立责任，不是术语堆叠：

| 概念 | 不可替代责任 |
|---|---|
| Pipeline | 一次terminal完整physical topology |
| Segment | 可stream/fuse的最大typed区域 |
| Breaker | stateful边界、order与资源Owner |
| Kernel | typed/representation-specific实现选择 |
| Frame | admission后的actual operation state |
| Morsel | shared scheduler消费的bounded ordinal work |

## 6. 验证审查

### 6.1 新证据

- targeted stateless/stateful differential：`2/2 PASS`；
- `group-relation` qualification：runtime `86/86`、processor `34/34`、Java 8 consumer/negative/javap
  全部`PASS`；
- 1M `frontier-source`与`frontier-relation`：correctness/fingerprint `PASS`；
- JFR定位stateless Chunk kernel与stateful GroupState/Row visitor边界；
- 结果证明Candidate模型可解释current code、resource与performance。

### 6.2 Evidence reuse

VP1/VP2已有PLAIN/encoded/RLE/overlay、ordered materialization、parallel/resource证据；M1 S1-S6已有
Canonical/Bound/Reference/Physical/Frame、Relation/Group与全局qualification。它们只作为稳定baseline，
本专题没有重复宣称为新implementation evidence。

### 6.3 未外推

本审查不声称：

- production已使用新Segment/Breaker模型；
- GroupBy/Join已获得新parallel algorithm；
- 所有operation已经vectorized；
- benchmark数字是SLA；
- formal promotion或implementation authorization已完成。

## 7. Readiness与授权边界

Candidate已经具备正式晋升审查条件。正确顺序是：

```text
Product Owner reviews frozen Candidate
    -> formal Design promotion proposal
        -> promoted Design baseline freeze
            -> targeted implementation readiness review
                -> explicit implementation authorization
                    -> activate P1 only
```

不能用本次“完成设计阶段”的授权替代production implementation authorization，也不能用历史I0-I8、M1
或VP1-VP3授权自动扩张。

## 8. 剩余非阻断观察项

这些不是待裁决设计债务，只能由实施期证据触发：

- GroupBy是否值得partitioned partial aggregation；
- Relation-left locator bridge是否值得消除；
- 哪些Mapped/Primitive stateless stages值得新增Chunk kernel；
- breaker是否需要更紧的phase-local liveness estimate；
- explain中哪些diagnostic对profile最有价值。

没有证据时保持current behavior，不预建placeholder。

## 9. Final disposition

```text
Candidate Design        FROZEN
Design work remaining   NONE
Feasibility             PASS
Overdesign findings     CLOSED
Promotion readiness     READY_FOR_PRODUCT_OWNER_REVIEW
Implementation          NOT_AUTHORIZED / NOT_STARTED
Active implementation   NONE
Release/publication     NOT_AUTHORIZED
```
