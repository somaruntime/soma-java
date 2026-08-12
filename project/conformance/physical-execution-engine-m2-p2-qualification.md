# Physical Execution Engine M2 P2 资格

类型：Conformance / Implementation Qualification

状态：`PASS / P2_COMPLETED / P3_ACTIVE`

日期：2026-08-12

Owner：Unary stateless typed Segment topology、P2 replacement与evidence

## 1. 实现结论

唯一`CanonicalPhysicalPipeline`现在按element shape拥有有限、顺序的typed Segment：`ROW_LOCATOR`、
`MAPPED_REFERENCE`与`PRIMITIVE`。每个Segment一次记录stage range、typed kernel、callback barrier与Morsel；
正常Table/Field/Mapped/Primitive terminal在取得Group guard、完成binding与resource admission后，直接消费final
PhysicalPlan中的对应Segment，不再在family executor中重新发现stateless边界。

Mapped reference继续使用host-reference callback kernel，primitive继续使用unboxed raw-value kernel；P2没有引入
universal Object batch、per-row plan node或generic executor。Field projection仍属于Row segment中的schema-known
logical projection；element-shape转换只在Mapped/Primitive segment边界发生。

`_explain()`现在一次planning后呈现Segment数量、terminal shape、kernel、callback barrier、stage range、Morsel与
sink；diagnostic不再为了scratch信息重复构造PhysicalPlan。

## 2. 边界

- 未新增public/generated API、artifact、dependency或Canonical node；
- Row/Mapped/Primitive的typed scalar loop是已准入kernel，不以抽象统一为理由引入boxing；
- callback前后没有重排，Reference Interpreter不读取PhysicalPlan；
- Relation-left的Mapped/Primitive输入仍由Relation执行器交付，等待P5纳入binary Pipeline；P2未建立临时
  adapter或第二套plan；
- stateful stage后的Breaker与Frame state由P3承接，本slice不提前实现。

## 3. Evidence

- targeted topology/differential/lifecycle：4 tests，0 failure/error；覆盖Mapped与Primitive typed Segment、
  `_explain()`、独立Reference differential、one-shot与callback failure；
- runtime full suite：87 tests，0 failure/error；processor：34 tests，0 failure/error；
- `./scripts/check.sh`：PASS，包括Java 8 build/generated consumer、三个reference application与local package；
- 10K `frontier-source`，3 fresh JVM runs，correctness/fingerprint PASS：Field sum 0.205 ms、materialize
  0.038 ms、Mapped primitive 0.249 ms、Mapped reference 1.100 ms；
- 1M `frontier-source`，3 fresh JVM runs，correctness/fingerprint PASS：Field sum 0.294 ms、materialize
  0.336 ms、Mapped primitive 18.177 ms、Mapped reference 15.404 ms、typed filter 3.497 ms；P1的
  sum 0.294 ms、materialize 0.326 ms与typed filter 3.393 ms保持同量级，未触发`15% + 2 ms`或
  sub-ms守卫；
- `git diff --check`：PASS；public/generated surface无修改。

固定主机数字是qualification evidence，不是SLA。

## 4. Replacement closure

正常Table-backed Mapped与Primitive terminal统一通过`executeMappedFamily`/`executePrimitiveFamily`把完整
terminal request交给同一个planner；family visit只读取`pipeline.terminalSegment()`的shape与stage range。
Planning、resource estimate、Frame和execution之间不存在第二个stateless boundary Owner。Relation输入的
既有边界已明确排入P5，不把尚未迁移的binary family伪装为P2完成事实。

## 5. Disposition

P2 exit全部满足，允许激活P3。Release/publication仍`NOT_AUTHORIZED`。
