# SOMA Physical Execution Engine M2 正式晋升与实施准入

类型：Conformance / Formal Promotion / Baseline Freeze / Authorization

状态：`PASS / FORMALLY_PROMOTED / BASELINE_FROZEN / IMPLEMENTATION_AUTHORIZED / P1-P5_COMPLETED / P6_ACTIVE`

日期：2026-08-12

Owner：M2 Candidate晋升、正式Baseline、readiness、授权与release边界

## 1. 结论

Product Owner审核并批准冻结Candidate后，Pipeline、Segment、Breaker、Kernel、ExecutionFrame与Morsel
责任已分别晋升到Planning、Execution、Architecture和Core正式Owner。没有新增public/generated API、
artifact、dependency、Java版本或产品语义；本次变化属于M1 internal responsibility revision。

```text
Candidate review             APPROVED
Formal Design promotion      PASS
Formal baseline              FROZEN
Implementation readiness     PASS
Implementation authorization GRANTED (2026-08-12)
Active slice                 P6
Release/publication          NOT_AUTHORIZED
```

## 2. Candidate provenance

冻结Candidate ID：

```text
soma-physical-execution-engine-m2-candidate:4a89960258ab3689a788d3e22b0a117587f4268bd92fc23841f973ea0bd942f7
```

Candidate保留在`project/temp/soma-physical-execution-engine-m2-governance/`直到P6 replacement closure；
实施期间它只保存设计过程和provenance，不再覆盖正式Owner或Engineering Plan。

## 3. 正式Baseline

| Owner / Plan | SHA-256 |
|---|---|
| `project/design/planning-and-optimization.md` | `ee5b6c44a3b6f77b51792c29d3cdd194950599770b36547e4d32d501b3d7618b` |
| `project/design/execution-and-concurrency.md` | `3ab0c30a4effb13e03b57c559e616201c5bc78d1c675e59ea2331231c7fbb450` |
| `project/design/implementation-architecture.md` | `199a3cc4bb33fcb279f872647d63d5d2967388f150e5f171fb259a80c7c23ef0` |
| `project/design/core-abstractions-and-narratives.md` | `b798f608e60f3d53b36c147ac6701018f835ce7b6ba25464c0a38bf4e6a4ae5b` |
| `project/engineering/physical-execution-engine-m2-implementation-plan.md` | `190824aeabb988819260d1579b154ff82c3f1b2c83465f4fdc80cda19c16cc01` |

该Baseline冻结责任、生命周期、不变量、P1-P6顺序、stop rules和证据边界，不冻结private class name、
threshold、partition count、hash coefficient或benchmark数字。

## 4. Readiness

- current-state inventory覆盖Row、Field、Mapped、Primitive、Group、Relation、Selection与point边界；
- two bounded feasibility journeys证明stateless Segment与stateful Breaker模型能解释现有代码和profile；
- Reference、resource、parallel、failure、mutation publication与performance guard均有正式Owner；
- P1-P6均是独立可验收纵向slice，没有big-bang prerequisite；
- general DAG、universal container、第二scheduler、runtime codegen、dynamic spill与SOMA Engine已排除；
- source baseline为`develop@6595c51`，开始实施时worktree无未识别用户修改。

## 5. 授权合同

授权Codex按正式
[P1-P6计划](../engineering/physical-execution-engine-m2-implementation-plan.md)自主修改production code、
tests、qualification、benchmark/profile、Design/Conformance projection并创建本地干净提交。一次只允许一个
active slice。

以下情况必须停止：Blueprint/Design产品语义变化、新dependency、第三artifact、public/generated API变化、
Reference独立性无法保持、resource admission无法在state前成立、证明链无法闭合或性能与正确性取舍。

该授权不包含GitHub Release/Package、Maven publication、签名、正式release声明或远端artifact发布。

## 6. 当前状态Owner

本记录拥有promotion/readiness/authorization；[P1资格](physical-execution-engine-m2-p1-qualification.md)已经
`PASS`，[P2资格](physical-execution-engine-m2-p2-qualification.md)、
[P3资格](physical-execution-engine-m2-p3-qualification.md)和
[P4资格](physical-execution-engine-m2-p4-qualification.md)与
[P5资格](physical-execution-engine-m2-p5-qualification.md)也已`PASS`，当前P6 active。每个P1-P6资格记录拥有其implementation evidence；
Engineering Plan只冻结顺序。P6完成前active bounded Temporary仍存在，完成后必须晋升稳定事实并删除。
