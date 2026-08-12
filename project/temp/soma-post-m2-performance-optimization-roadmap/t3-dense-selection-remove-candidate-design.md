# T3 Dense Selection Remove 候选设计

类型：Temporary / Candidate Design / Active Slice

状态：`PASS / IMPLEMENTED / QUALIFIED / SLICE_CLOSED`

日期：2026-08-13

## 1. 目标与边界

本切片只优化普通 Selection remove 的正常生产路径。它不改变 Table-local all-or-nothing、
zero-publication、packed compaction、canonical locator order、compression transparency、
structured failure 或 public/generated API。

正式 Design 已规定：Selection remove 先冻结 dense move plan，构造完整 replacement Key/Index，
完成全部可恢复分配与验证，最后才执行无分配 payload commit 或 candidate root swap。本切片不改变
该协议，只删除 replacement sidecar 构造中的重复工作。

明确排除：

- 不引入 rollback journal、delta Index、MVCC、tombstone Table state或第二套membership truth；
- 不把 Selection terminal 拆成多次 point remove；
- 不新增 Batch/Loader/public mutation API；
- 不改变 encoded/overlay candidate publication边界；
- 不为benchmark引入schema或场景特供分支。

## 2. Current baseline 与根因

固定主机、Corretto 8、1M rows、1 warmup + 3 samples、fresh process：

| Path | Selection remove median | Fingerprint |
|---|---:|---|
| `SomaCompression.OFF` | 166.491 ms | PASS |
| `SomaCompression.AUTO` | 214.444 ms | PASS |

工作负载删除`quantity < 10`，即约1% rows；Table拥有一个Key和三个secondary Index。CPU profile
确认主要production stack进入`IdentityHashIndex.rebuild/addStored/findStored`。当前实现虽然已经退出
PLAIN payload candidate copy，但replacement sidecar仍然：

```text
final locator mapping
    -> 对每个final row重新读取Field
        -> 重新hash
            -> 重新typed equality定位Bucket
                -> 重新经历Shard/Bucket growth
```

这些工作重复发现old sidecar已经拥有的Bucket identity、hash和membership。AUTO fallback还从已经冻结的
`SelectionRemovePlan`重新复制、排序、二分删除集合来形成candidate payload。

## 3. Candidate mechanism

### 3.1 Operation-scoped locator projection

`SelectionRemovePlan`构造一个operation-scoped `old locator -> final locator + 1` primitive array：

- `0`表示该old locator被删除；
- 正值减一得到final locator；
- identity survivor保持原locator；
- packed tail survivor映射到对应hole。

它不是Table state、reverse Index或长期缓存，publication后立即由operation释放。

### 3.2 Sidecar structural projection

每个old Key/Index Shard直接构造独立replacement：

1. 保留Bucket hash与open-addressing slot topology；
2. 按locator projection过滤removed membership并改写survivor locator；
3. multi Bucket只为最终survivor创建一个canonical ascending `int[]`；
4. 空Bucket成为replacement Shard中的tombstone；空Shard退出；
5. 完整计算replacement managed bytes；old sidecar保持不变。

该路径仍是线性membership projection，但不再读取业务Field、重新hash、重新比较Bucket identity或逐条
rehash/grow。Key仍保持0..1，secondary Index仍保持完整duplicate membership。

### 3.3 Payload path

- PLAIN：使用同一frozen plan构造replacement sidecar，随后执行既有无分配row move、tail clear与
  descriptor publication；
- encoded/overlay：直接使用plan的removed/move集合构造candidate directory，不重新排序或二分；
  sidecar同样从old sidecar投影，随后candidate root一次发布。

## 4. Invariants 与资源

- old payload/sidecar在final commit前不修改；任一structured failure保持root/version/data/index不变；
- replacement sidecar不共享未来会被point mutation修改的Shard或Bucket array；
- final Bucket locator严格递增，所有survivor恰好出现一次，removed locator不出现；
- new payload上的first locator仍拥有与Bucket hash一致的typed logical value；
- locator projection、replacement Shard/Bucket与candidate payload全部属于既有conservative mutation
  temporary admission；不得在admission前分配；
- `OutOfMemoryError`继续透传，不转成structured failure；checked structural/resource failure发生在
  publication前。

## 5. 实施与退出证据

实施只允许以下内部变化：

1. `SelectionRemovePlan`拥有直接payload move与old-to-final projection；
2. `IdentityHashIndex`增加replacement structural projection；
3. `GeneratedTable`让PLAIN与encoded Selection remove共享该projection；
4. 删除旧的row-by-row projected rebuild private path，不保留双production机制。

退出必须同时满足：

- Key、singleton/multi Index、empty Bucket、tail-to-hole、AUTO fallback与reference payload测试；
- fault/resource rejection继续zero publication；
- `IdentityHashIndex.validateForTesting`通过；
- 1M OFF/AUTO及10M AUTO fingerprint PASS；
- 相对current baseline无可重复退化，且CPU/profile证明row hash/equality rebuild退出；
- runtime targeted suite、`./scripts/check.sh`、package smoke与`git diff --check`通过；
- 稳定机制晋升正式Owner/Conformance，Temporary candidate完成replacement closure。

上述退出证据已经全部闭合；正式结果由
[T3 Conformance](../../conformance/v1-dense-selection-remove-sidecar-projection-governance.md)拥有。
