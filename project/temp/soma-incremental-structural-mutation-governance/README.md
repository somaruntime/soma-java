# SOMA 32 位结构域与即时增量 Key/Index 维护治理

状态：`ACTIVE / CANDIDATE_B_PRODUCT_OWNER_APPROVED / S1-S3_IMPLEMENTED_AND_QUALIFIED / S4_ACTIVE`

初始日期：2026-08-10

最后更新：2026-08-11

本目录服务当前唯一 active bounded governance topic：把 SOMA 的 Table-local 可寻址结构迁移到
32位结构域，并修正point/Selection mutation的Key/Index维护架构；64位累计域和Schema-defined业务
数值域继续承担确实需要`long`的语义。

Product Owner 于2026-08-11冻结此前“延迟失效 + tagged locator + dirty Bucket + 25% cleanup”方案，
将其保留为candidate A；当前active candidate B采用raw `int` locator、即时Key/Index membership、
自研typed Hash directory与升序`int[]` Bucket。Product Owner已批准candidate B及其实施顺序；S0以
`a0f1cd9`固化立即linked-posting predecessor baseline，S1已由`3d90184`完成32位结构域迁移，S2由
`51fd7b2`完成singleton-inline + ordered `int[]`替换；S3全读取路径与完整`./scripts/check.sh`已通过。
当前只剩S4专项性能/资源证据、正式Owner晋升与Temporary replacement closure。

## 当前入口

- [当前候选设计 B](DESIGN.md)：32位结构域、即时增量 Key/Index维护、升序`int[]` Bucket、实施边界
  与Exit；
- [冻结候选 A](CANDIDATE-A-DELAYED-STRUCTURAL-MAINTENANCE.md)：此前延迟结构维护方案，只用于比较
  与追溯，不再授权实施；冻结内容SHA-256为
  `f22f95ed4486116cc031609930e3d2e7866df98b663352ec43a2d2b1a0d82349`；
- [实施交接](HANDOFF.md)：S0隔离、最后已验证 checkpoint 与已退役实验；
- [后续 Logical IR 治理意图](FUTURE-LOGICAL-IR-GOVERNANCE.md)：下下次专题的 queued intent，当前不实施。

## 当前边界

- candidate A已冻结，不得继续修订或作为implementation input；
- candidate B是唯一active且已获Product Owner批准的implementation Design input；
- candidate B固定32位结构域、64位累计域、Schema-defined业务数值域、dense packed remove、immediate
  Key/Index membership、自研typed Hash directory、singleton inline与multi升序`int[]` Bucket；
- 第一版以linear scan和`System.arraycopy`作为internal baseline，但不把具体算法晋升为长期Design合同；
- 不引入fastutil、延迟失效、第二套Index truth、未来加速strategy/SPI或background cleanup；
- 项目正式 `project/design/` 尚未修改，本专题不能伪装成 current formal product fact；
- linked-posting只存在于`a0f1cd9` predecessor checkpoint；active checkout已删除next-links并只维护一套
  singleton-inline / multi `int[]` membership truth；
- 未完成`LocatorBitmap` experiment已删除，scheduling refactor已冻结在本专题之外；
- implementation按“worktree/base evidence → 32位结构域迁移并qualification → `int[]` Index替换并
  qualification”的连续single-active-slice顺序推进；
- JSON/DAG、第二套 Index、reverse Index、background cleanup、new dependency和public physical API不在
  本次范围。
