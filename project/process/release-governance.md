# Release 治理

类型：Process

状态：正式

Owner：SOMA Java release 过程

事实范围：release candidate 的证据类别、授权边界和声明规则

非事实范围：当前 readiness、真实账户/联系人和产品语义

最后审查日期：2026-07-30

## 1. Release profile 与功能完成分离

功能、性能和 package mechanics 通过，不等于任意渠道都可以发布。每次 G6
必须先声明 selected release profile，再只对该 profile 的适用义务形成证据；
不适用的 public/publishing 义务不能伪装成已通过，也不能反向阻塞一个严格更窄的
private-source profile。

当前长期区分：

| Profile | 含义 | 不自动证明 |
|---|---|---|
| private GitHub source | 受控访问者从 private SCM 获取 source 并协作开发 | public community、Maven artifact、production SLA |
| public GitHub source | 向公众分发 source 并承担公开治理与安全响应 | Maven Central binary distribution |
| Maven/binary | 通过批准的 artifact repository 分发不可变制品 | 任意未列平台、任意性能或 production suitability |

Codex Cloud development readiness是开发环境结论，不是 release profile，也不能替代
G6。

## 2. 共同证据与按 profile 适用证据

所有 selected profile 都必须具备：

- 真实 copyright owner、LICENSE/NOTICE 与使用授权；
- 真实 SCM、project/issue、maintainer、support 和 private-security path；
- clean immutable source commit 与可追踪 Git provenance；
- 与 profile 相称的 package/reproducibility、dependency/SBOM/vulnerability/license
  evidence；
- 使用者交付物采用显式 allowlist，并验证最终内容、checksum 与 provenance；
- external consumer、适用 Gate 与已批准 JDK/OS/architecture support matrix；
- 明确的 version、claim boundary、withdrawal/rollback 过程和 Owner sign-off。

只有 public GitHub profile 才要求公开 community/CoC enforcement、公开披露路径和
public source distribution provenance。只有 Maven/binary profile 才要求最终
binary/source/javadoc/checksum、signing/OIDC、publishing account/endpoint 与
artifact distribution provenance。未选择的 profile 保持 `not-selected`，不能写成
ready 或 waived。

本地 placeholder、dirty package、snapshot 名称或同一机器多个 JDK vendor 都不能
替代真实 Owner、clean candidate 和支持矩阵。

### 2.1 Private-source 的两种 source surface

完整 Git repository 与精简源码交付物不是同一表面：

- repository 包含 `project/`、测试、benchmark、CI 和正式治理事实，服务开发、
  审查与同 commit 追踪；
- curated source archive 由
  `scripts/manifests/source-release-files.txt` 允许集合生成，服务离线构建和
  reference consumer 取证；
- archive 不包含 `project/`、repository fixtures、CI、原始 evidence、本机文件
  或 credential；
- 依赖完整Design Owner的Agent Skill不进入缺少`project/`的archive；需要时从
  同一immutable commit的完整authenticated repository获取；
- archive 内容必须在解包后以 exact toolchain 和 Maven reactor 重新验证，不能
  仅凭原 checkout 已通过构建推断 allowlist 完整；
- source archive 的 commit、version、内容集合和 checksum 必须进入同 SHA
  qualification evidence。

托管平台提供的 repository snapshot 视为完整仓库快照，不能冒充 curated
source archive。

## 3. 授权与平台限制

Push、tag、publish、签名、仓库 visibility 变化和公开 readiness 声明属于外部状态
变更，必须得到明确授权。缺少真实 profile 事实时状态保持 blocked，不创建虚假
URL、联系人、账户或 approval。

仓库 plan 不支持某项 branch protection/ruleset 时，Report 必须记录真实平台限制、
现有控制与残余风险；不得伪造成功配置。只要该控制不是 selected profile 已裁决的
硬前置，平台限制本身不自动否定其他已经形成的证据。

## 4. 输出

Release Report 必须区分：

- selected profile 的 passed/blocked；
- Codex Cloud development readiness；
- 未选择的 public/Maven profile；
- 当前已实现能力、本机 mechanics、已验证支持和禁止声明。

正式支持只能来自已批准且重放通过的 matrix。Private-source G6 passed 不得写成
public release、Maven Central、production readiness 或公开性能承诺。
