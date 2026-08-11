# SOMA Java V1 Result 与 Structured Failure Design

类型：Design

状态：Active V1 Baseline

正式事实源：是

Owner：Normal outcome、Result carrier、failure code/operation/context、mapping、precedence、
sanitization与失败后状态保证

最后审查日期：2026-08-03

## 1. 设计目标

Normal absence/no-op用normal result表达；contract/runtime failure用stable
`SomaOperationException`表达。Failure必须machine-readable、fail-closed且不能让application
冒充SOMA code。

## 2. Normal outcome matrix

| Operation | Normal result |
|---|---|
| `find(key)` missing | `Optional.empty()` |
| `get(key)` missing | failure `MISSING_KEY` |
| point update missing | `UpdateResult.matched() == 0 && changed() == 0`，callback不执行 |
| point remove missing | `RemoveResult.removed() == 0` |
| point/selection update | `UpdateResult(matched, changed)` |
| empty query | count 0 / empty Optional/List/array |
| predicate no match | normal empty/zero |
| reserve below capacity | normal no-op |
| valid mutation no logical value change | matched > 0, changed 0, no version change |

Absence不能用null、sentinel或exception混合表达。

## 3. Result carriers

```java
public final class UpdateResult {
    public int matched();
    public int changed();
}

public final class RemoveResult {
    public int removed();
}
```

Invariants：

```text
0 <= changed <= matched
0 <= removed
```

`matched`是terminal冻结后的selection membership数量；`changed`是callback结束后至少一个
non-Key logical Field值与old value不同的record数量，不是setter invocation或changed leaf数量。
同一record多次setter最多计一。`changed==0`时mutation不发布新stateVersion；`changed>0`时整次
operation只发布一个新version。`removed`同理按被删除membership计数，zero不改变version。
这些Result描述一次单Table mutation，最大值不超过Table结构域，因此使用`int`；Stream、Relation、
Group等可能跨输入或产生派生组合的count/cardinality继续使用`long`。

Changed comparison沿用Schema/Storage logical equality：primitive（含float/double canonical
semantics）、String content、Enum identity、Value structural；ordinary Object/reference payload
只按Java reference identity `==`（含null）判断。不同referent即使`equals()`为true仍是changed；
同一referent内部被application修改不属于Table logical publication，既不增加changed也不更新
stateVersion。

Carrier immutable、detached、无public constructor/mutator，不携带View、row position、root、
continuation、collection或mutable storage。Carrier object identity不是合同；runtime可以复用常见
immutable zero/one outcome，application只能观察其值。

## 4. Exception carrier

```java
public final class SomaOperationException extends RuntimeException {
    public SomaFailureCode code();
    public SomaOperation operation();
    public String context();
    @Override public Throwable getCause();
}
```

- runtime/factory内部构造；application不能注入code；
- `code + operation`是machine-readable contract；
- message/context用于diagnostic，不要求parse；
- cause只在安全且有意义时保留；
- raw internal path、schema private value、worker/Executor/array/address不泄漏。

## 5. Operation kinds

```text
CONFIGURE
RESERVE
ADD
FIND
GET
UPDATE
REMOVE
QUERY
```

Join、Group、sort、materialization与`_explain()`均属于QUERY；不泄漏physical phase。

## 6. Failure codes

| Code | 含义 |
|---|---|
| `INVALID_ARGUMENT` | null/negative/range/type/owner/dependency/Join component等调用合同无效 |
| `DUPLICATE_KEY` | add违反唯一Key |
| `MISSING_KEY` | required point lookup missing |
| `MISSING_RELATION_SIDE` | Outer Join访问missing side |
| `NULL_VALUE_UNSUPPORTED` | Java 8 Optional等terminal无法表达present-null |
| `RESOURCE_LIMIT_EXCEEDED` | managed budget、cardinality、array/container/representation/task peak不足 |
| `ARITHMETIC_OVERFLOW` | SOMA-owned checked numeric/cardinality/version arithmetic overflow |
| `CONCURRENT_GROUP_OPERATION` | same Group operation overlap |
| `REENTRANT_GROUP_OPERATION` | callback重入same Group |
| `NESTED_PARALLEL_OPERATION` | callback内启动parallel terminal |
| `CONFIGURATION_FROZEN` | freeze后重复configure |
| `PARALLEL_EXECUTOR_UNAVAILABLE` | pool shutdown/rejection/cancellation required task |
| `OPERATION_CANCELLED` | caller interrupt后的同步cancel/quiescence |
| `PIPELINE_ALREADY_CONSUMED` | linked pipeline node已被intermediate claim或terminal consume后再次使用 |
| `CALLBACK_SCOPE_VIOLATION` | 可检测View/Editor owner/execution/participant/epoch越界 |
| `CALLBACK_FAILED` | application callback/comparator/mapper/ordinary equals/hashCode抛Exception |

No generic `INTERNAL_ERROR`作为可恢复catch-all。Unexpected JVM `Error`按第10节处理。

## 7. Invocation mapping

| 场景 | Result/failure |
|---|---|
| null required argument/componentType | `INVALID_ARGUMENT`；nullable String/Enum Index lookup的null bucket除外 |
| add/update staged Key或outer/nested Value为null | `INVALID_ARGUMENT` |
| negative reserve/skip/limit/top/budget | `INVALID_ARGUMENT` |
| `between(lower, upper)` lower > upper | `INVALID_ARGUMENT` |
| empty `in()` | normal predicate，永不match |
| reference `in(...)`包含null | `INVALID_ARGUMENT` |
| wrong Table/Group/composition expression | `INVALID_ARGUMENT` |
| mapped array primitive `Class`或value incompatible | `INVALID_ARGUMENT` |
| duplicate Key | `DUPLICATE_KEY` |
| get missing | `MISSING_KEY` |
| point update/remove missing | normal zero Result；callback不执行 |
| Outer pair missing accessor | `MISSING_RELATION_SIDE` |
| Optional-family terminal选择到present-null | `NULL_VALUE_UNSUPPORTED` |
| materialization/pipeline peak超budget | `RESOURCE_LIMIT_EXCEEDED` |
| checked SOMA integer/cardinality overflow | `ARITHMETIC_OVERFLOW` |
| floating aggregate产生NaN/Infinity | normal IEEE-754 result；不是structured failure |
| callback `Math.addExact`/RuntimeException | outer `CALLBACK_FAILED` |
| pool rejection/shutdown | `PARALLEL_EXECUTOR_UNAVAILABLE` |
| same Group overlap/reentry | respective concurrency code |

Typed expression construction中的self-contained literal failure使用`operation=QUERY`，不freeze
configuration、不取得Group guard或消费任何linked pipeline。Intermediate/terminal argument失败
同样发生在pipeline claim前；state-dependent failure遵守第11节后续phase。

## 8. Callback ownership

Application callback、Comparator及arbitrary mapped reference `equals/hashCode`抛出的普通
`Exception`统一包装为当前outer operation的`CALLBACK_FAILED`，原异常只作cause。Application
不能借这些边界注入或重放SOMA runtime failure code。String/Enum/generated Value specialized
equality属于SOMA internal path，不调用application override。

同时，callback内调用borrowed SOMA surface可能由runtime同步产生真正的structured failure，
例如missing Join side、scope、same-Group reentry。Implementation为内部exception携带不公开、
不可构造的operation provenance；只有“由当前operation runtime产生且token匹配”的failure保留
原code。Application创建、保存后重放、foreign operation或provenance不匹配的
`SomaOperationException`都包装为`CALLBACK_FAILED`。Public exception不暴露provenance member。

SOMA在callback外产生的structured failure也保留原code。Callback side effect不回滚，属于
application责任；Table authoritative state仍保持zero publication。

## 9. Context sanitization

Stable context只允许必要logical identity与safe numeric facts，例如：

- Table/Field/Index logical name；
- operation argument category；
- required/available/estimated bytes；
- canonical element/work-unit ordinal；
- configured/frozen state。

禁止：absolute filesystem、source body、ordinary referent `toString()`、raw callback value、
Key secret、ClassLoader、Executor/thread/task、array/address、internal stack/plan class。

## 10. JVM Throwable boundary

- Application `Exception`按callback mapping；
- SOMA validation/allocation/planning可恢复failure使用structured code；
- `OutOfMemoryError`、`StackOverflowError`、`LinkageError`、`ThreadDeath`等`Error`不包装成
  normal Soma failure；
- Error传播前implementation执行best-effort quiescence/lease release，但不能承诺JVM继续安全；
- OOME不能成为绕过preflight或返回partial state的正常路径。

## 11. Failure phase precedence

```text
1 invocation / argument / owner / pipeline validation
2 pipeline consume + reentrancy / nested-parallel validation
3 Group admission
4 terminal binding and state-dependent missing/duplicate
5 arithmetic / representation / memory / Executor preflight
6 expression / callback / hash / comparator / aggregate execution
7 candidate schema / Index / compression validation
8 atomic publish
9 quiescence / result handoff
```

Earlier phase wins。Same phase按canonical Field source order、element position或work-unit ordinal；
parallel不使用wall-clock first。Short-circuit只承认canonical decisive frontier内failure。

Phase 1失败不claim open linked pipeline；Phase 1成功的intermediate原子claim predecessor并只
留下新child为open，成功的terminal原子consume receiver。Terminal Phase 2及其后任何outcome都
保持consumed。`PIPELINE_ALREADY_CONSUMED`属于Phase 1 pipeline-state validation。Reusable
source不进入该状态机。

## 12. Failed-state guarantee

任何structured failure返回时必须同时成立：

- no partial Result；
- no partial authoritative Table publication；
- payload/Key/Index/compression/accounting仍属同一old root；
- version unchanged；
- worker quiescent；
- temporary lease与Group guard释放；
- no hidden retry/fallback/spill；
- callback external side effect不声称回滚。

若commit mechanism不能保证publish之后不再发生可恢复throwing work，该mechanism不得进入
production。

## 13. Handling guidance

Application按code处理expected boundary：

```java
try {
    table.parallel().filter(...).count();
} catch (SomaOperationException failure) {
    switch (failure.code()) {
        case RESOURCE_LIMIT_EXCEEDED:
            // shrink application request or increase explicit budget
            break;
        case CALLBACK_FAILED:
            // inspect safe cause; Table state unchanged
            break;
        default:
            throw failure;
    }
}
```

Application不应parsemessage/context或依赖internal phase。

## 14. Explicit absence

- checked/public Result wrapper for every success；
- `Optional`作为failure carrier；
- null/sentinel negative counts；
- user-constructible failure code；
- generic unsupported/internal error；
- nondeterministic suppressed worker failure；
- transaction rollback claim for callback/external side effect；
- raw physical diagnostic in stable failure。

## 15. Evidence Gate

Implementation必须验证：

- every result invariant/normal absence；
- every code至少一个positive failure path；
- phase precedence与parallel canonical arbitration；
- callback wrapping、current runtime provenance保留、application replay/foreign exception拒绝；
- Error passthrough；
- sanitization/secret/non-deterministic context negatives；
- fault injection at every mutation/planning/allocation phase；
- zero publication、version/root/index/accounting与quiescence。
