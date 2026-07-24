# SOMA 三层性能基线治理

类型：Temporary

状态：active

Owner：SOMA three-layer performance baseline governance

事实范围：三层性能证据责任、baseline artifact、环境适用性、比较规则、校准、Gate、正式固化与退役条件

非事实范围：SOMA public/generated API、annotation Schema 语义、Access Model、runtime 语义、跨环境 SLA、支持矩阵和 release readiness

最后审查日期：2026-07-24

## 1. 意图

把 SOMA 已有的领域中性 component benchmark 和两个 reference application
multi-fork diagnostic，从“可以重复测量”提升为“能够在明确环境、固定 workload
和 correctness guard 下持续识别性能回归”的正式工程体系。

性能 evidence 必须服务于 Design，而不是由某次本机数字反向定义产品。治理只建立
基线责任、artifact、比较器、校准和 Gate；本机结果继续保持
`claimAllowed=false`，不包装为 public SLA 或跨机器结论。

## 2. 三层责任

1. **Component Performance Baseline**：由 `soma-benchmarks` 拥有，隔离测量
   Candidate Scan、exact index、Column traversal、allocation、retained memory 等
   领域中性 mechanics；
2. **Reference Application Integrated Performance Baseline**：由两个 child
   application 各自拥有，测量固定领域 workload 下的 end-to-end hot operation、
   allocation、GC、runtime high-water 和结果 identity；
3. **Public Performance Evidence / Claim**：只在环境、workload、统计强度和审批
   足够时形成。当前没有此层 claim，所有本机 artifact 必须继续声明
   `claimAllowed=false`。

前三者不是三个数字等级。前两层可以进入回归 Gate；第三层是受审批约束的对外声明
边界，不能由本地 Gate 自动晋升。

## 3. 目标

- 为三层 evidence 建立唯一 Owner、输入、输出、适用边界和升级规则；
- 为 component 与两个 integrated lane 建立版本化 baseline artifact；
- artifact 记录 workload、correctness identity、实际 Zulu JDK、JVM、OS、
  architecture、commit、fork、warmup、measurement 和指标语义；
- comparator 区分 `PASS`、`FAIL` 和 `NOT_APPLICABLE`，环境不匹配不得伪装成
  性能通过或失败；
- correctness、allocation、GC、retained/high-water 和 timing 使用与稳定性相称的
  比较规则，不用一个宽泛百分比掩盖指标差异；
- timing 使用多个独立 JVM fork 的稳健统计量和校准证据，不接受单次 wall-clock；
- baseline 更新必须显式、可审计，不允许正常 Gate 静默重写；
- baseline runner、parser、comparator 和负路径拥有相称测试；
- `./scripts/check.sh` 保持唯一综合入口，并明确 smoke、baseline Gate 与 public
  claim 的区别。

## 4. 非回归约束

治理不得删除、改变或弱化：

- SOMA public/generated API、annotation Schema、Access Model、ownership、Index
  生命周期、失败原子性或 runtime protocol；
- component lane 的 Access Pattern 覆盖、checksum、allocation 和 exact-index
  memory evidence；
- 两个应用的领域语义、配置、结果 identity、correctness/default/large/long-run
  workload、oracle/invariant/lifecycle evidence；
- 两个应用的 production/test 隔离、ordinary consumer dependency 和独立 ownership；
- 多独立 JVM fork、allocation/GC/runtime high-water 和
  `claimAllowed=false`；
- Azul Zulu full JDK 8 作为当前唯一 compiler/validation authority；
- generated footprint、schema hash、external consumer 和现有完整 Gate。

任何 baseline 都必须绑定具体 workload 和环境。不得为了让 Gate 通过而删 lane、
缩小数据规模、减少 correctness guard、放宽领域结果，或把 setup 混入/移出测量以
改变既有指标含义。

## 5. 授权与停止边界

项目 Owner 已授权使用 Goal Mode 完成本专题，可以修改 benchmark、两个 reference
application 的 test/evidence、fixture、脚本、checker、Engineering、
Implementation Map、Conformance、Report 和 Temporary，并进行阶段性 Git 提交。
可以新增不进入 production runtime 的 Java 8 evidence tooling；不引入第三方依赖。

出现以下情况必须停止并请求决定：

- public/generated API 或 annotation Schema 发生不兼容变化；
- Access Model、runtime 语义、ownership、Index 生命周期或失败原子性变化；
- reference application 领域语义、场景目标或 correctness evidence 被改变；
- 需要增加第三方依赖、其他 JDK authority 或 public performance claim；
- 需要扩大到本专题之外的产品设计。

## 6. 阶段

1. **Stage 0：协议与基线审计**——冻结起点、Owner、现有 artifact、环境和缺口，
   运行完整 Gate，提交 immutable starting point；
2. **Stage 1：详细设计**——裁决 artifact schema、环境指纹、指标分类、比较状态、
   校准、更新纪律、负路径和聚合 Gate；
3. **Stage 2：Component baseline**——实现领域中性 baseline、comparator 和 Gate；
4. **Stage 3：Integrated baseline**——为两个应用补齐环境身份、baseline 和
   application-owned comparison；
5. **Stage 4：校准与候选**——执行稳定多 fork 校准、负路径、专项/聚合/完整 Gate，
   形成 immutable implementation candidate；
6. **Stage 5：正式收口**——原子固化 Engineering、Implementation Map、
   Conformance 和 Report，完成 scope non-regression，删除 Temporary 并提交。

每个 slice 必须独立正确，不得依赖未来重写才成立。

## 7. 完成条件

- 三层责任、claim 升级边界和唯一 Owner 无歧义；
- component 与两个 application baseline 都版本化、环境感知且不可被普通 Gate
  静默改写；
- comparator 对不适用环境 fail-closed 地给出 `NOT_APPLICABLE`，不产生假结论；
- deterministic identity、allocation、GC、high-water 和 timing 都有明确规则；
- 多 fork 校准证据支持阈值，且所有 checked-in 本机 baseline 保持
  `claimAllowed=false`；
- 专项 Gate、`./scripts/check.sh` 和 `git diff --check` 全部通过；
- public API、Schema、runtime、两个应用能力和现有 evidence 没有缩水；
- 正式 Governance Report 形成，长期事实进入唯一正式 Owner；
- 引用闭包完成，Temporary 删除，`docs/README.md` 恢复无 active topic，全部授权
  修改已提交且工作树干净。

## 8. 当前材料

- [Stage 0 当前证据审计](current-evidence-audit.md)
- [Stage 1 详细设计](detailed-design.md)
