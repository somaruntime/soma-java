# 当前性能摘要

类型：Report / 性能快照

状态：候选

Owner：SOMA Java 性能输出

受众：评估当前 runtime 形状和后续优化价值的维护者

事实范围：指定 commit、环境和方法下的 packed exact component 与 FJSP 诊断结果

适用版本：commit `b991f4c`

测量日期：2026-07-20

环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，aarch64

方法：component runner 2,000 warmup + 5,000 measurement；FJSP 100,000 operations，同机2次warmup + 5次measurement A/B

输入事实源：[Packed Exact Index 切换后尾项治理报告](../../../reports/2026-07-20-packed-exact-index-post-cutover-closeout-report.md)

最后审查日期：2026-07-20

## 1. 当前能力

当前实现使用 packed `[0,size)`、swap-remove、eager grouped exact index与table-local `IndexBuffer`。`IndexSnapshot`对empty/single结果采用共享或inline表示，对multi-index保持detached copy；exact-index row-link与group capacity独立，append/replace按实际distinct groups预检。FJSP machine availability由application-owned indexed min-heap维护，Machine Table仍是事实源。

## 2. Component 诊断

`./scripts/check-post-cutover-components.sh`在2,000 warmup、5,000 measurement下记录：

| Lane | allocated B/op |
|---|---:|
| exact source → count | 116.1696 |
| exact source → filter → count | 400.0736 |
| exact source → filter → sort → single snapshot | 648.0960 |
| exact source → filter → sort → materialize | 1,085.0784 |

各lane measurement window均为0次Young/Full GC。Single snapshot相对开发期同runner前置观测减少`16 B/op`；这只是当前JVM诊断。

65,536 rows / 16 distinct groups的exact-index primitive retained payload由`3,211,264 B`降至`787,024 B`（`-75.492%`）；65,536 distinct groups时保持`3,211,264 B`，没有牺牲worst-case unique形状。该数字排除JVM object header与alignment。

当前component artifact SHA-256：`a232d7c4033d738844c5f876b4579ade5578aebf23213d60dbd718e545c14460`。

## 3. FJSP machine-selection A/B

100,000 operations，同机2次warmup、5次measurement：

| 中位数指标 | dynamic machine sort | application heap | 变化 |
|---|---:|---:|---:|
| solve time | 443.032250 ms | 207.396500 ms | -53.187% |
| solve allocation | 458,683,080 B | 369,316,168 B | -19.483% |
| total allocation | 672,136,808 B | 585,138,456 B | -12.944% |

两侧5/5结果的assignments、completed jobs、makespan、tardiness、checksum与RuntimePlan hash一致。Dynamic/heap artifact SHA-256分别为`eccee52369d85c164da7a6ce5a822529c7a6d830fda428d5ab979466c7392c8a`和`f23eb2eb6441f5ea5581c815c62c5b873636574c90aa3aff121f897c822e92cb`。

## 4. 解释边界

这些结果支持当前component allocation/cardinality形状，以及FJSP该workload采用application heap的场景决策；不支持跨机器、跨workload、production SLA或正式支持矩阵。所有artifact均为`claimAllowed=false`。

后续性能工作仍必须保留caller-responsibility Index契约、resource preflight、collision full equality、mutation atomicity、packed swap-remove和public API兼容；不能为减少allocation恢复dirty rebuild、maintained order或unsafe stable Index。
