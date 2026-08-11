# SOMA Canonical Logical IR 与执行引擎正式晋升和实施准入审查

类型：Conformance / Formal Promotion / Baseline Freeze / Implementation Readiness

状态：`PASS / FORMALLY_PROMOTED / BASELINE_FROZEN / READY_FOR_IMPLEMENTATION /
IMPLEMENTATION_AUTHORIZATION_NOT_GRANTED`

正式事实源：是

Owner：Canonical Logical IR与执行引擎M1责任调整的晋升范围、冻结指纹、实施准入与授权边界

日期：2026-08-11

## 1. 审查结论

本次审查通过。SOMA当前Java frontend的计算语义与执行主线已经从bounded Candidate正式晋升到
Planning、Core、Implementation Architecture与Execution唯一Owner；S1-S6实施计划具备开始实施所需
的目标、slice、exit evidence、性能守卫、stop rule与防过度设计边界。

结论严格区分：

```text
Formal Design promotion      PASS
Baseline freeze              PASS
Implementation readiness    READY
Implementation authorization NOT_GRANTED
Production implementation   NOT_STARTED
Release/publication          NOT_AUTHORIZED
```

因此，当前没有active implementation slice。Product Owner需另行明确授权后，S1才能激活；I0-I8历史
授权与资格不能自动扩张到本次M1重构。

## 2. 基线与审查范围

启动时production source baseline：

```text
branch: develop
source HEAD: a6988e7c0c593bc0739d977dbbc34e92f3e5e5cd
date: 2026-08-11
```

启动时worktree只包含本专题治理文档。审查读取current formal Blueprint、九个Design Owner、I0-I8
Engineering/G1-G10、current code/IR inventory、post-governance Conformance与Candidate材料；本次没有
修改production source、generated API、test、benchmark、build、dependency或workflow。

本记录不重跑未改变输入的全量qualification，也不把历史benchmark改写成新性能证据。它审查的是
Design completeness、Owner closure、迁移可执行性、证据预算与授权边界。

## 3. Design baseline fingerprint

冻结集合：

| Formal Owner / Plan | SHA-256 |
|---|---|
| `project/design/planning-and-optimization.md` | `d2b429cea6ed719591b3a2057e7df6261566b06d7d835db2f5782e6e95539a75` |
| `project/design/execution-and-concurrency.md` | `30a83fd4583f83c3ed3b6df866b791cf8fdfd13757d8f70ac7fe01dd52e2d359` |
| `project/design/implementation-architecture.md` | `11bc6f0d1eb441985dd08906013e60dac8b9122d55bc9e35887adc43b81d3a96` |
| `project/design/core-abstractions-and-narratives.md` | `2d792e4a388ec69ac9c1094d91e5078702cafb95993e270a3f939103996f9dcc` |
| `project/design/README.md` | `9c32e938947a63b84d21bbf042decfbd3385911d38a28bd61d4eea82762ac1c3` |
| `project/engineering/canonical-ir-execution-engine-implementation-plan.md` | `eecf4e03d0da4bf131243f3426b8013a76fa7c380ffbdd289ee7ce930bfaa93d` |

按上述文件顺序对标准`shasum -a 256`输出再次取SHA-256，Baseline ID为：

```text
canonical-ir-execution-m1:489f3f76234a31ce9e2140da715908e79b092484397f4eca667f04d72a9916e8
```

该指纹冻结规范性文本，不冻结private class name、package layout、hash/codec coefficient或偶然test
shape。实施若改变本集合中的合同，必须按Core M0/M1/M2协议重新分类；M2必须回到Product Owner。

## 4. 正式晋升矩阵

| Concern | Unique Owner after promotion | 晋升内容 | 不拥有 |
|---|---|---|---|
| Canonical semantics | [Planning](../design/planning-and-optimization.md) | Java lowering边界、compact identity、type/shape/literal、Canonical/Bound/Normalized、Predicate、ExecutionRequest | public API、StateRoot、actual scratch |
| Physical decision | [Planning](../design/planning-and-optimization.md) | PhysicalPlan decision、ResourceEstimate、rewrite/Index/Join/Group choice | actual lease、workers、publication |
| Cross-Owner skeleton | [Core](../design/core-abstractions-and-narratives.md) | A16-A20、N3、INV-12的层间责任与proof route | 精确class或算法 |
| Runtime seams | [Architecture](../design/implementation-architecture.md) | Java-lowering-to-frame组件边界、specialized family与no-universal-executor | 用户语义、failure matrix |
| Operation lifecycle | [Execution](../design/execution-and-concurrency.md) | Group guard、actual resource admission、ExecutionFrame、scheduler、quiescence | normalization、physical decision、StateRoot truth |
| Slice/evidence | [Engineering Plan](../engineering/canonical-ir-execution-engine-implementation-plan.md) | S1-S6、exit、performance、stop、防过度设计 | implementation authorization |

Blueprint、Logical API、Generated Signature、Storage与Failure产品合同没有变化。Current Java generated
API仍是唯一正式frontend；SOMA Engine只作为未来consumer约束，不是本专题implementation surface。

## 5. 冻结的主合同

```text
Java generated facade
    -> JavaLowering
        -> immutable CanonicalOperation
            -> terminal-start BoundOperation
                +-- ReferenceInterpreter
                +-- Normalize / semantic rewrite
                        -> NormalizedOperation
                            -> PhysicalPlan + ResourceEstimate
                                -> resource admission
                                    -> operation-local ExecutionFrame
                                        -> specialized sequential / parallel execution
```

关键不变量：

- Java facade、GeneratedProbe、StateRoot、physical Index与locator不成为Canonical identity；
- identity复用compiled composition descriptor/capability与Table/Field/Index ordinal；
- Field direct source只有`TableSource + FieldProject`一个Canonical形态；
- schema-known literal直接暴露immutable typed leaves，不复制第二个probe；
- arbitrary mapped reference使用host shape，不伪装成schema LogicalType；
- reference从Bound层分叉，不消费Normalized/Physical decision且不成为production fallback；
- PhysicalPlan只拥有decision/estimate，ExecutionFrame只在lease成功后拥有actual short-lived state；
- sequential/parallel保留specialized kernel，semantic统一不等于boxed universal executor；
- current linear/binary需求不准入general DAG、prepared/cache、public SPI或serialization格式。

## 6. Readiness review

| Check | Result | Reason |
|---|---|---|
| 产品目标与边界 | PASS | 服务Java frontend高性能计算，并只保留future frontend extension seam |
| Unique Owner | PASS | Canonical/decision/admission/frame/state/failure已分责，无第二事实源 |
| Operation coverage | PASS | Row、Field、Mapped、Primitive、Relation、Group与Selection均有迁移slice；direct point/control明确在IR外 |
| Lifecycle/failure/resource | PASS | claim、guard、bind、plan、lease、frame、publish、quiesce顺序闭合 |
| Correctness oracle | PASS | reference直接解释Bound，三路独立state differential作为exit evidence |
| Performance protection | PASS | specialization、no-boxing/no-O(N)-node、10K/1M与risk-triggered 10M/FJSP守卫明确 |
| Migration closure | PASS | family-by-family vertical replacement，slice退出不允许双IR/bridge |
| Evidence proportionality | PASS | targeted-first，输入未变不重复重型qualification，S6再全量闭合 |
| Overdesign control | PASS | 无第三artifact/dependency/SPI/DAG/JSON/Workflow/backend placeholder |
| Stop rules | PASS | M2、scope expansion、double truth、late admission与performance/correctness取舍均显式阻断 |

未发现需要Product Owner继续裁决的Design P0/P1。实现期若代码反例证明上述合同无法同时成立，按
stop rule暂停，不能在code中静默缩小范围。

## 7. Evidence reuse 与实施期新证据

本次准入重用current已通过证据作为baseline，而不是重新宣称：

- I3 typed IR/reference/optimized query；
- I4 Selection/resource/failure；
- I5 Relation/Group；
- I6 parallel；
- post-governance operation provenance与sidecar admission；
- current 10K/1M/10M performance frontier与100K FJSP场景。

这些旧证据只能证明治理前baseline。S1-S6必须按新Plan为changed family补充new lowering、layer ownership、
reference/physical differential、admission-before-allocation、generated body delta与性能防退化证据；S6
才能重新闭合受影响G4/G5/G6/G7/G9。

## 8. Temporary replacement closure

原bounded topic的长期事实已完成替换：

| Temporary input | Replacement Owner |
|---|---|
| current-state audit | 本记录第2、4、6节与current code executable fact |
| Candidate Design | Planning/Core/Architecture/Execution |
| migration/qualification plan | 正式S1-S6 Engineering Plan |
| topic status/decision log | 本记录与Conformance index |
| SOMA Engine future concept | 独立queued product concept Temporary，不作为本专题Design |

因此Canonical IR治理Temporary不再拥有active事实，应在本次变更中退役，不保留parallel Candidate
archive。未来SOMA Engine构思独立保存为inactive、non-Design input。

## 9. Authorization 与 release boundary

本次Product Owner批准的是正式晋升、Baseline Freeze与实施准入审查，不等于授权S1-S6 production
implementation。后续若授权，仍必须一次只推进一个active slice，并在exit evidence、一次bounded
独立审查、Conformance更新和干净提交后进入下一项。

本记录不授权：

- public/generated语义变化；
- 新dependency、第三production artifact或public/internal SPI；
- SOMA Engine、JSON、Workflow、dynamic schema或set-based mutation；
- GitHub Release/Package、Maven remote publication、签名或正式release声明。

## 10. Final disposition

```text
Promotion finding            CLOSED
Design completeness          PASS
Baseline integrity           PASS
Implementation readiness     READY
Remaining Design decision    NONE
Active implementation slice NONE
Authorization               WAITING_FOR_PRODUCT_OWNER
```

下一步不是继续扩写Design，而是在Product Owner明确授权后按正式Plan激活S1。
