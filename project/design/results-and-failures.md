# Result 与 Structured Failure Design

类型：Design

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 normal outcome、Result carrier、failure carrier/code、mapping、precedence 与失败后状态保证

上游：[SOMA Java V1 产品蓝图](../blueprint/README.md)

最后审查日期：2026-08-01

## 1. 设计目标

V1 清楚区分正常业务 absence/no-op 与 operation contract failure。普通调用不使用
sentinel、message parsing、checked exception 或通用 `Result<T,Error>`；失败必须
machine-readable、stable、sanitized 且 fail closed。本 Design 承接 BP-7 和 BP-10。

## 2. Normal outcome matrix

| Operation | 正常 absence/no-op | Contract failure |
|---|---|---|
| `find(key)` | `Optional.empty()` | invalid argument/value |
| `get(key)` | 无 normal missing | missing 为 `MISSING_KEY` |
| point `update` | logical no-op 为 `UpdateResult(1,0)` | missing 为 `MISSING_KEY` |
| selection `update` | no match 为 `UpdateResult(0,0)` | structured failure |
| point `remove` | missing 为 `RemoveResult(0)` | structured failure |
| selection `remove` | no match 为 `RemoveResult(0)` | structured failure |
| `add` | success 返回 `void` | duplicate 为 `DUPLICATE_KEY` |
| query `findFirst` | empty 为对应 Optional | selected null/reference contract failure |

Normal absence 不应该靠 exception 表达；需要存在的 point operation 则使用 `get`/
point update 的明确 missing failure。

## 3. Result carriers

V1 shared Result types 位于 library public API namespace
`io.github.somaruntime.soma.api`，使用 immutable detached `int` count：

```java
public final class UpdateResult {
    public UpdateResult(int matched, int changed);
    public int matched();
    public int changed();
}

public final class RemoveResult {
    public RemoveResult(int removed);
    public int removed();
}
```

Contracts：

- `0 <= changed <= matched`；
- point update 只可能 `1/0` 或 `1/1`，missing 不返回 Result；
- point remove 只可能 `0` 或 `1`；
- selection count 对应成功一次发布的 final selection；
- Result 不携带 Record、row position、cursor、version、continuation、collection 或
  live Table handle；
- Result object 的 allocation 只发生在 terminal/direct-operation result boundary。

V1 不提供 `AddResult`。Count 受 Table `int size/capacity` domain 约束；实现必须在
计算和 conversion 前 checked arithmetic。

## 4. Exception carrier

所有 recoverable SOMA operation contract failure 统一使用 unchecked：

```java
public final class SomaOperationException extends RuntimeException {
    public SomaFailureCode code();
    public SomaOperationKind operation();
    public SomaFailureContext context();
}
```

V1 不为每个 code 建 subclass。`code` 是稳定 machine contract；message 只供人阅读，
不得被 application 解析；cause 保存 diagnosis provenance。

P2 已固定最小 immutable context carrier：

```java
public final class SomaFailureContext {
    public SomaFailureContext(String table, String fieldPath);
    public String table();
    public String fieldPath();
}
```

`table`/`fieldPath` 都必须 non-null；不适用 operation 使用唯一 absence representation
empty String `""`。合法 Table/Field logical identity 不为空，因此没有二义性。不能自动
调用任意 Key/Object `toString()`。

Stable identity contract：

- `table()` 使用 generated Table facade FQCN，不使用 schema declaration name、短名或
  physical identity；
- `fieldPath()` 使用 Table-relative dot-separated logical path；
- composition/global operation 为 `("", "")`，Table-level operation 的 Field path 为空；
- failure 精确归属于一个 Field 时使用该 path；candidate 同时存在多个 Field failure 时，
  按 schema source order 选择最早的 invalid logical Field；无法诚实归属单个 Field 时
  保持 empty，不能猜测；
- record position、Key value 和 Index bucket 不进入 context。

若 production
implementation 证明需要额外 stable bound/count/position，必须先变更本 Design 和
consumer compatibility Gate，不能通过 message 或 implementation-specific Map
偷偷扩张。

Exact constructor/package/enum projection 见
[Generated Java API Signature Design](generated-api-signatures.md)。

## 5. Operation kinds

V1 stable enum：

```text
CONFIGURE_PARALLEL
RESERVE
ADD
FIND
GET
UPDATE
REMOVE
QUERY
```

Operation kind 表达 user operation family，不泄漏 internal phase、kernel、worker 或
storage algorithm。

`size/capacity/_metadata`、Index/pipeline construction 与所有 read-only Stream terminal
使用 `QUERY`；`reserve` 使用 `RESERVE`；Update/Remove terminal method 自身的 argument
failure 使用对应 terminal kind。Parallel pool failure 保持当前 terminal 的 operation
kind，而不是另造 internal scheduler kind。

## 6. Failure codes

| Code | 语义边界 |
|---|---|
| `INVALID_ARGUMENT` | invocation、pipeline parameter、component type、owner/shape 非法 |
| `INVALID_VALUE` | detached input/candidate 违反 generated schema/value contract |
| `MISSING_KEY` | `get` 或 point update 所需 Key 不存在 |
| `DUPLICATE_KEY` | keyed add candidate 与 existing Key 冲突 |
| `NULL_VALUE_UNSUPPORTED` | non-null schema position、natural order 或 reference `findFirst` 无法接受 null |
| `ARITHMETIC_OVERFLOW` | SOMA-owned checked integer aggregate、size/growth/offset arithmetic 溢出 |
| `RESOURCE_LIMIT_EXCEEDED` | known cardinality、array/collection/capacity/scratch/task bound 不可满足 |
| `CONCURRENT_TABLE_OPERATION` | Table shared/exclusive admission conflict |
| `REENTRANT_TABLE_OPERATION` | callback 重入来源 Table direct operation/terminal |
| `NESTED_PARALLEL_OPERATION` | 任意 SOMA callback 内启动 parallel terminal |
| `PARALLEL_CONFIGURATION_CONFLICT` | fixed pool 被 different instance 替换 |
| `PARALLEL_EXECUTOR_UNAVAILABLE` | fixed pool shutdown/reject/unavailable |
| `STREAM_ALREADY_CONSUMED` | one-shot pipeline 被再次 terminal |
| `CALLBACK_SCOPE_VIOLATION` | 可检测 Record/Editor/View callback scope、Table、execution、participant/thread 越界 |
| `CALLBACK_FAILED` | application callback 抛普通 `RuntimeException` |

Enum 不包含 `CURRENTNESS_FAILURE` 或 `UNSUPPORTED_OPERATION`：

- late binding/admission/no-live-handle 使 currentness 成为 internal contract；
- unsupported capability 从 generated API 缺席，在编译期失败。

新增、删除、重命名 code 是 public compatibility change，必须通过 Design change、
consumer Gate 和 release policy。

### 6.1 Invocation mapping matrix

| Trigger | Stable code |
|---|---|
| null Table input/key/callback/comparator/mapper/updater；negative reserve/skip/limit；foreign endpoint；invalid `toArray` component | `INVALID_ARGUMENT` |
| detached/callback candidate 的 non-null Value/Key leaf、declared reference type 或 schema constraint 失败 | `INVALID_VALUE` |
| natural String/Enum order 观察 null；reference `findFirst/min/max` 最终选中 null | `NULL_VALUE_UNSUPPORTED` |
| point required Key absent / keyed add duplicate | `MISSING_KEY` / `DUPLICATE_KEY` |
| `setParallelExecutor(null)` | `INVALID_ARGUMENT` |
| fixed parallel config 被 different instance 替换 | `PARALLEL_CONFIGURATION_CONFLICT` |
| SOMA-owned integer result/size/version/offset overflow | `ARITHMETIC_OVERFLOW` |
| known array/cardinality/scratch/task bound 不能表示或满足 | `RESOURCE_LIMIT_EXCEEDED` |

Generated immutable Value/shared Result/failure carrier 的 public constructor 在没有 Table
operation context 时违反自身前置条件，使用普通 `IllegalArgumentException`；一旦进入
SOMA Table/Pipeline operation，则只使用上表 structured mapping。Generated detached
Table object 的 constructor/setter 允许暂时不完整，validation 延迟到 add/update boundary。

Application callback 自己抛出的 `ClassCastException` 是 `CALLBACK_FAILED`；callback 已
正常返回后，generated reifiable return-boundary 检查发现 raw/generic heap pollution 才是
`INVALID_VALUE`。Parameterized ordinary Object 只检查 erasure，不扫描 type argument。

## 7. Context sanitization

Failure context 只能携带稳定 logical fact。禁止：

- arbitrary Key/Object automatic stringify；
- physical row/column ordinal、cursor、backing array；
- raw Executor/ForkJoinPool、worker/thread、task/scratch；
- implementation class、hash bucket、memory address；
- secret、credential、full filesystem path 或 user payload dump。

Message 可以提供人类诊断，但不得成为唯一可执行信息，也不得破坏 sanitized boundary。

## 8. Callback mapping

- Application callback 抛普通 `RuntimeException`：映射为 `CALLBACK_FAILED`，原异常
  作为 cause；
- Mapped reference `distinct/sorted/min/max` 调用 application object 的
  `equals/hashCode` 或 Comparator，视为 callback phase；其 RuntimeException 同样映射为
  `CALLBACK_FAILED`；
- callback 中 application `Math.addExact` 的 `ArithmeticException` 也属于
  `CALLBACK_FAILED`，因为 arithmetic 不由 SOMA 实现；
- callback 合法访问另一张 Table/顺序 operation 得到的 `SomaOperationException`
  保持原 code/operation/context/cause，不二次包装；
- `OutOfMemoryError`、`StackOverflowError`、`LinkageError` 等 `Error` 不包装成
  recoverable SOMA failure；
- Error 若发生在 publish 前仍必须保持 Table zero partial publication，但进程/JVM
  是否继续可靠不由 SOMA 承诺。

Application 在调用 SOMA 前使用普通 Java `+` 发生的 binary wrap 已经丢失 overflow
fact，SOMA 不猜测。需要 application-side checked arithmetic 时使用 `Math.addExact`
并检查 `CALLBACK_FAILED` cause。

## 9. Failure phase precedence

一次调用同时存在多个潜在 failure 时，固定 phase：

```text
1. invocation / pipeline / argument validation
2. reentrancy / nested-parallel validation
3. Table admission
4. state-dependent validation (missing / duplicate)
5. resource / Executor preflight
6. callback / filter / map / sort / aggregate
7. candidate schema / Key / Index validation
8. atomic publish
```

最早 phase 获胜。Direct operation 的 detached input 若必须先验证才能确定 operation
identity/Key，其基础 argument/value validation 属于 phase 1；phase 7 是 callback/
staging candidate 的 publish-before validation。

Phase 1 内部固定为：basic null/range/type/owner validation，随后才检查 linked/consumed
Stream state。因此对已经 consumed 的 pipeline 传入 null callback，结果仍是
`INVALID_ARGUMENT`；合法 argument 才得到 `STREAM_ALREADY_CONSUMED`。Argument/
reentrancy validation 尚未成功时不消费 fresh pipeline。

只有 effect 已知后才能成立的 next-stateVersion overflow 在 publish-before validation
检查：Update 必须先确定 `changed > 0`，Remove 必须先确定 non-empty selection。它不让
logical no-op 失败；为确定 effect 已执行的 callback failure 可以按实际 phase 更早获胜。

同一 parallel phase：

- 有 Record/Field position 时选 canonical encounter position 最早的 failure；
- 无单一 position 的 sort/merge 使用固定 work-unit order；
- short-circuit 只承认 canonical decisive frontier 内的 failure；
- frontier 后 speculative failure 不覆盖顺序 result；
- 一次 operation 只暴露一个 primary failure；
- 不附加 nondeterministic suppressed worker failure。

## 10. Failure-state guarantee

任何 `SomaOperationException` 返回给调用方时必须满足：

- 没有 partial Result；
- 没有 partial Table publication；
- payload、Key、Index、size、capacity 和 internal stateVersion 保持 operation 前可信；
- parallel workers 已 quiescent；
- resources/staging 已释放到 operation-defined safe state；
- earlier independent successful operation 保留；
- external callback side effect 不回滚。

Atomic publish 开始前必须完成所有 recoverable checks。Implementation bug 或 JVM
Error 不可以伪装成 structured partial-success Result。

## 11. Typical handling

```java
try {
    table.stream()
        .filter(predicate)
        .update(updater);
} catch (SomaOperationException failure) {
    switch (failure.code()) {
        case ARITHMETIC_OVERFLOW:
        case RESOURCE_LIMIT_EXCEEDED:
        case CONCURRENT_TABLE_OPERATION:
        case REENTRANT_TABLE_OPERATION:
            handle(
                failure.code(),
                failure.operation(),
                failure.context());
            break;
        default:
            throw failure;
    }
}
```

Application 应根据 enum/code 和 operation/context 决策，message/cause 主要用于日志和
debugging。

## 12. 明确排除

- checked operation exception；
- exception subclass per code；
- string code 或 message parsing；
- generic `Result<T,Error>` 包裹每次调用；
- sentinel `null/-1/NaN` 表达 core failure；
- public currentness/unsupported failure；
- live Result、continuation 或 retry handle；
- nondeterministic worker failure exposure；
- OOME/Error 伪装为 recoverable resource failure；
- external side-effect rollback promise。

## 13. Implementation admission Gates

Production runtime/API 必须固定并验证：

- public package、constructor、accessor、enum values 与 serialization boundary；
- point/selection normal outcome matrix；
- every stable code 的 positive/negative trigger；
- context sanitization 和 no arbitrary stringify；
- callback/nested SOMA/Error mapping；
- phase combination、parallel arbitration 与 short-circuit frontier；
- failed operation zero publication、worker quiescence 和 state trust；
- message 不被 consumer/test 用作 machine contract；
- no excluded carrier/code/API surface。
