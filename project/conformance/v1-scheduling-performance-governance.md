# SOMA V1 Scheduling 性能治理与通用运行时优化资格

类型：Conformance / Post-V1 Scenario and Runtime Performance Governance

状态：`PASS / FORMALLY PROMOTED / TEMPORARY REPLACEMENT CLOSED`

日期：2026-08-11

Owner：标准100K FJSP的phase-separated性能事实、profile驱动的通用SOMA优化、example访问路径
最佳实践、正确性证据与剩余性能边界

## 1. 最终结论

标准`1,000 Jobs × 100 Operations × 3 Processing Options` FJSP journey在保持四张SOMA Table、
自然waiting add/remove、machine-first FCFS + SPT、100,000条结果与makespan `50,281`不变的前提下：

```text
初始纯dispatch：18,736 ms
最终fresh-JVM纯dispatch：590 / 601 / 624 ms
最终warm median：509–516 ms
```

Fresh-JVM median相对初始dispatch降低约`96.8%`，约`31.2x`；完整journey在`-Xmx1g`下PASS。
Product Owner提供的手写算法`<300ms`经验值仍未达到：当前同机warm gap约`1.7x`，fresh median约`2.0x`。
它继续作为后续算法/engine优化参照，不被改写成已经达成的SLA。

本专题没有加入FJSP专用SOMA API、shadow authoritative state、fixed slots、application
PriorityQueue、新dependency或第二套Index机制。Runtime中保留的每项优化都服务所有Table/point
mutation，并由正式Storage、Execution和Result Design承接；FJSP规则只存在于example solver core。

## 2. 归因方法与固定边界

| 项目 | 固定事实 |
|---|---|
| Host | Apple M5 Pro，48 GiB，macOS arm64 |
| Java | Amazon Corretto `1.8.0_502` |
| Standard input | 1000 Jobs、100 Operations/Job、100 Machines、3 candidates/Operation |
| Correctness | 100,000 operations、makespan 50,281、独立result validator PASS |
| Normal JVM | `-Xms128m -Xmx1g -XX:+UseParallelGC` |
| Warm JVM | `-Xms1g -Xmx1g`，fresh Group，2–3 warmups，5–7 odd samples |
| Profile | async-profiler CPU/allocation JFR与collapsed stacks |

`SchedSolveResult`将SOMA runtime initialization与纯dispatch分开；model factory和solve后的独立
validation不进入dispatch。初始phase证据为约`155ms initialization + 18,736ms dispatch`，因此
原18秒不是输入工厂或结果校验造成，而是正常求解热路径。

绝对时间只属于本机证据，不是跨硬件SLA。async-profiler allocation collapsed stack的sample
weight只用于热点比较，不解释为精确allocation bytes或live memory。

## 3. Profile驱动实施

### 3.1 通用SOMA runtime

| 发现 | 通用修正 | 正式Owner |
|---|---|---|
| PLAIN point remove复制整个Chunk | 全部preflight/fault injection完成后，在exclusive guard final commit中执行bounded swap-remove与reference clear，再发布新header | Storage encounter order与point mutation |
| encoded overlay每次update复制不断增长的overlay | sparse overlay超过64次变更后把hot Chunk保持为PLAIN，避免反复materialize/re-encode | Storage AUTO/hot-Chunk policy；threshold仍是internal |
| shared-secret accessor每次结果创建执行`Class.forName` | access已安装后直接读取缓存，只在首次初始化时触发class initialization | Internal architecture，不改变public surface |
| PLAIN point remove仍申请整root temporary | 对allocation-free PLAIN final commit使用zero temporary；encoded/candidate path保持保守admission | Execution resource Owner |
| 每次operation/result建立短命carrier | non-blocking Group guard复用internal lease；callback depth按thread复用；常见immutable zero/one Result复用 | Execution guard与Result identity合同 |
| resource path重复drain queue | 每次真实nonzero admission只drain一次；configuration/global metadata规则不变 | Global resource admission |

这些修正不放宽Table-local atomicity、failed-state、managed accounting、currentness、callback scope、
reference clearing或same-Group concurrency rejection。Runtime 65项测试全部PASS；hot encoded Chunk
新增65次point mutation回归，证明AUTO转为PLAIN后payload、Index与metadata一致。

### 3.2 Scheduling application

- `MachineWaitingOperation.operationId`退出secondary Index：求解热路径已经由immutable
  `OperationModel.processingOptions()`持有精确option IDs，删除waiting rows直接走Key；不为无consumer
  的访问方向维护Index；
- machine与waiting选择仍读取SOMA Table/Index，但固定FCFS+SPT使用复用的borrowed `View` accumulator，
  不为每一道工序构建通用sort/top计划或物化中间List；
- 选中的machine/waiting值直接从callback-scoped View复制到solver-local scalar，不再为随后point get
  创建detached record；
- `OperationState`仍是ready/status/result的权威Owner；同一次point update callback读取ready/status、计算
  start/completion并发布结果，删除此前冗余的get + update双operation；
- hot option traversal使用indexed List access，避免每道工序创建短命Iterator。

以上只是访问模式与算法实现优化。四张Table的事实Owner、waiting natural lifecycle和结果查询API不变。

## 4. Before / after 与Profile收敛

| 阶段 | 纯dispatch | 解释 |
|---|---:|---|
| Baseline | 18,736 ms | point remove Chunk copy + encoded overlay copy |
| PLAIN in-place remove | 8,360 ms | dense swap-remove不再复制Chunk |
| hot overlay→PLAIN | 1,291 ms | OperationState重复update退出overlay复制热点 |
| shared-secret first-use cache | 892 ms | repeated `Class.forName`退出热路径 |
| access-path与低分配收口 | 590–624 ms fresh / 509–516 ms warm | 无冗余operationId Index/List、无selector detached records，复用通用carrier |

最终CPU profile 654个sample weight中的主要inclusive路径：machine selection `121`、waiting remove
`95`、successor release `71`、waiting selection `34`。此前Chunk arraycopy、overlay mutableCopy和
repeated `Class.forName`已退出主热点。allocation profile总sample weight从`1052`降到`770`
（约`-26.8%`）；剩余主要是immutable `TableStateRoot` publication、Index Bucket growth、Query plan与
真实waiting row/result对象。

继续追逐`300ms`最直接的方案是引入application machine/frontier heap或新的ordered access path；
前者会制造第二套运行时选择状态，后者是新的SOMA capability/Design。两者都没有在本专题中以
“临时优化”偷渡。

## 5. Qualification 与最佳实践

- scheduling focused tests：5项PASS；
- SOMA runtime：65项PASS，point mutation、failed-state、resource、concurrency回归通过；
- 三次fresh JVM：initialization `145/144/123ms`，dispatch `624/590/601ms`，全部makespan `50,281`；
- 7-sample warm measurement与CPU/allocation profile均使用fresh Groups，最终独立validator PASS；
- `./scripts/check.sh`闭合Maven、I0-I8 breadth、三个reference applications、benchmark compile、
  package/source consumer、SBOM/provenance与workflow pin检查。

由本专题形成的使用准则：

1. 只为真实consumer建立Index；已有稳定Key/OOP identity时，不再为同一访问重复建立secondary Index；
2. 小而高频的数据源采用单次borrowed View traversal表达固定业务minimum，通用sort/top留给动态计划；
3. 热循环不物化只用于取得scalar identity的detached record/List；
4. 同一record的read-modify-write在一个Editor callback中完成，避免冗余point terminal；
5. SOMA runtime优化必须跨场景成立，并由正式Design与通用回归承接。

## 6. Claim boundary

本记录允许声称：SOMA标准100K FJSP reference application已从不可接受的约18.7秒纯dispatch优化到
fresh约0.6秒、warm约0.51秒；收益由通用point-mutation/storage优化和合法application访问路径共同
产生，正确性、原子性与产品叙事未被削弱。

本记录不证明：`300ms`已达到、所有FJSP算法都具有相同结果质量、跨硬件SLA、ordered Index/
PriorityQueue已进入SOMA、超过一亿行资格、GitHub Release/Package、签名或正式release。

原[Scheduling reference application治理](v1-scheduling-reference-application-governance.md)继续拥有
建模、目录、功能与初始性能provenance；其中18秒数据是历史baseline，不再代表current性能。
bounded Temporary已完成replacement closure并删除。
