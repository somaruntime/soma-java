# S1 32 位结构域迁移矩阵

状态：`COMPLETE / S1 QUALIFIED`

日期：2026-08-11

Owner：S1 对当前正式 Blueprint/Design 与 executable surface 的精确 delta；只服务本次 bounded
治理，不提前改写正式 Owner。

## 1. 唯一数值域裁决

| 语义 | S1 类型 | 超界行为 |
|---|---|---|
| `@SomaTable.defaultCapacity` | `int` | Java annotation/source type拒绝不可表达常量；negative由processor拒绝 |
| Table `size/capacity/reserve` | `int` | add/growth超过`Integer.MAX_VALUE`时`RESOURCE_LIMIT_EXCEEDED` |
| raw locator、Chunk ordinal/offset | `int` | `-1`只作missing/end；其他negative为internal invariant failure |
| Key/Index membership、Table Selection locator buffer | `int` | array/container capability不足时`RESOURCE_LIMIT_EXCEEDED` |
| `UpdateResult.matched/changed`、`RemoveResult.removed` | `int` | 单Table结果不超过Table结构上限 |
| Table metadata `size/capacity` | `int` | 与同一published root同源 |
| Stream/Relation/Group `count/cardinality` | `long` | checked累计溢出为`ARITHMETIC_OVERFLOW` |
| relation encounter ordinal、`skip/limit/top` count | `long` | 保持现有cumulative/query合同 |
| `stateVersion` | `long` | checked递增溢出为`ARITHMETIC_OVERFLOW` |
| memory bytes、budget、retained/temporary peak | `long` | checked累计溢出为`ARITHMETIC_OVERFLOW` |
| application Field | Schema-defined | 不受结构域迁移影响 |

`Integer.MAX_VALUE`是最大合法Table size；最大合法locator是`Integer.MAX_VALUE - 1`。Public API不
暴露locator。所有byte计算先widen到`long`，不得用`int`乘加后再转换。

## 2. Owner delta

| Owner | 当前正式合同 | S1 candidate delta | S1 evidence |
|---|---|---|---|
| Blueprint BP-4 | 全logical size/position/count统一long-domain | Table-local addressable state改为int；cumulative domain继续long | generated consumer、near-boundary virtual tests、全仓qualification |
| Schema | `defaultCapacity(): long` | `defaultCapacity(): int` | processor positive/negative与full regeneration |
| Signature | Table `size/capacity/reserve`与mutation result为long | Table结构API/result改为int；query count保持long | source golden、`javap`、Java 8 consumer |
| Storage | long size/capacity/locator、long posting links | int root/header/addressing/membership；S1暂保留linked-posting算法 | storage/state-machine/index differential |
| Execution | Selection locator与Table-local result沿long | locator buffer/result int；relation/query累计仍long | reference/optimized/parallel与mutation differential |
| Failure | cardinality/resource/overflow统一映射 | 结构能力不足为`RESOURCE_LIMIT_EXCEEDED`；累计long溢出为`ARITHMETIC_OVERFLOW` | boundary与failed-state tests |
| Core INV-06 | checked long Table domain | checked int structural + checked long cumulative domain | invariant matrix与full qualification |

## 3. 实施边界

S1必须一起迁移：annotation/model/renderer、generated Table signature、TableStateRoot、Chunk directory、
Key/Index locator、Table/Field/Index query locator buffer、Selection mutation、relation内部row locator、
metadata与Table-local mutation result。

S1不改变：Key/Index linked-posting算法、Logical IR节点、Join/Group语义、compression representation、
parallel协议、public Field业务类型、memory budget类型、stateVersion、query count/cardinality。

## 4. 禁止的过渡态

- public `int` API进入runtime后再次无约束扩成long locator；
- root使用int但Index/Selection继续保存long membership；
- 同一语义同时保留int/long overload或compatibility alias；
- 通过cast截断long cumulative值；
- 在S1混入`int[]` Bucket替换、fastutil、第二套Index或未来strategy。

## 5. S1 Exit

1. production source中Table-local locator/size/capacity/membership不存在long-domain残留；
2. query count、Group/Relation cardinality、memory与version仍为long；
3. generated source/`javap`/consumer冻结新的exact surface；
4. near-zero、Chunk边界、near-`Integer.MAX_VALUE` virtual arithmetic与failure mapping通过；
5. runtime、processor、I0-I8 breadth、stable examples、benchmark compile、package与source consumer通过；
6. no S2 container replacement，S1以独立干净提交关闭。

## 6. Closure evidence

2026-08-11已形成以下可重放证据：

- `soma-runtime`：63项测试通过；Table root、Chunk、locator buffer、Selection、Key/Index linked
  posting与全部执行路径已使用checked `int`结构域；
- `soma-processor`：34项测试通过；`@SomaTable.defaultCapacity`、generated Table
  `size/capacity/reserve`与Table-local mutation result均冻结为`int`；
- 112-Table generated-surface scale fixture通过：448 Fields、224 Index、2,581 class files，未触发
  Java 8 method/constant-pool边界；
- `./scripts/check.sh`完整通过，覆盖reactor、I0-I8 breadth、三个stable examples、benchmark compile、
  package/source consumer、SBOM与provenance；
- `git diff --check`通过；定向残留检查只保留Group/Relation/Stream累计、memory、version及业务数值的
  合法`long`，历史目录与尚待最终晋升的正式Design不作为S1 executable source；
- S1没有引入`int[]` Bucket、fastutil、第二套Index truth或future strategy；linked posting算法保持不变，
  Index container replacement只由S2拥有。
