# Physical Execution Engine M2 P3 资格

类型：Conformance / Implementation Qualification

状态：`PASS / P3_COMPLETED / P4_ACTIVE`

日期：2026-08-12

Owner：Unary stateful finite Breaker topology、Frame state lifecycle与P3 evidence

## 1. 实现结论

唯一`CanonicalPhysicalPipeline`现在同时拥有完整typed Segment序列和finite Breaker序列。P3正式准入三种
stateful边界：`MEMBERSHIP`、`STABLE_REORDER`与`BOUNDED_TOP`；其kernel按Row locator、host reference和
raw primitive分别固定为typed hash、stable sort或typed heap，不存在universal boxed Breaker。

每个Breaker一次记录shape、kind、kernel、logical stage、consumed range、前后Segment ordinal与保守input
upper bound。Row、Mapped与Primitive执行器只消费这些descriptor，不再自行扫描stage来决定stateful
boundary或top算法。实际`IntLocatorBuffer`、`MappedValueBuffer`和`LongValueBuffer`只在resource lease成功、
Frame建立后分配，并由该Frame持有；membership/sort/heap状态也只存在于同一次Frame execution lifetime。

Row typed `order + limit`在既有资格阈值内成为`BOUNDED_TOP/TYPED_HEAP`；其他Row/Mapped/Primitive top仍以
stable sort segment加limit表达，保持`top == stable sort + limit`。Materialization继续是terminal sink，
没有被伪装成额外中间Breaker。

## 2. 边界

- 没有generic DAG、universal batch、Object tuple、第二scheduler或新dependency；
- Reference Interpreter继续按原Canonical stages独立执行，不读取Breaker descriptor；
- callback comparator仍是barrier，稳定次序与failure wrapping没有变化；
- Relation-left的stateful downstream仍属于P5 binary handoff范围；P3不提前复制Relation planner；
- P3没有准入新的parallel stateful merge，parallel只用于已有、已证明的upstream路径。

## 3. Evidence

- targeted stateful topology/differential：4 tests，0 failure/error；覆盖Row bounded top、Mapped
  distinct+stable sort、Primitive distinct+stable sort、descriptor/explain与typed result；
- stateful semantic matrix：6 tests，0 failure/error；覆盖callback order/failure、top 0/oversize/tie、
  bounded threshold、parallel upstream、Mapped hash failure与Primitive boxed Reference differential；
- runtime full suite：78 tests，0 failure/error；`./scripts/check.sh`：PASS，processor 34 tests、三个
  reference application和local package均PASS；
- 1M `frontier-stateful`，3 fresh JVM runs，correctness/fingerprint PASS：Field distinct 4.667 ms、
  sort-limit 9.166 ms、top 9.169 ms、Table top 11.726 ms、Table slice 2.550 ms；相对既有正式
  frontier资格中的4.546/8.977/8.730/15.413/2.526 ms处于同量级，未触发性能守卫；
- resource/admission、callback exactly-once/failure、detached materialization与temporary-zero-return由全量
  runtime/check矩阵重放；
- `git diff --check`：PASS；public/generated surface无修改。

固定主机数字是qualification evidence，不是SLA。

## 4. Replacement closure

production Row/Mapped/Primitive stateful optimized路径不再调用family-local `nextStateful`或
`primitiveNextStateful`。这些扫描只保留在独立Reference Interpreter中。PhysicalPlan是Breaker kind、
kernel与stage boundary的唯一production Owner；executor保留typed algorithm implementation，但不再拥有
planner decision。

## 5. Disposition

P3 exit全部满足，允许激活P4。Release/publication仍`NOT_AUTHORIZED`。
