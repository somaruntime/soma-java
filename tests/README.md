# 仓库级验证资产

`tests/fixtures/` 保存 compiler positive/negative case、public/generated `javap`
golden、schema JSON/hash golden 与独立 Maven consumer。它们由
[测试与 evidence Map](../project/implementation-map/test-and-evidence-map.md)登记，
通过 `scripts/` 下的能力 Gate 消费。

该目录不是 Maven module，不产出 artifact，也不得成为 production 或 reference
application 的 compile/runtime dependency。通用 helper 只有在两个以上真实
evidence owner 复用且能降低契约重复时才进入这里；单测试 helper 保留在使用方。

个别 fixture 内部的 package/schema name 属于既有 golden identity；只有相应
compatibility case 被批准变更时才同步更新，不作为仓库导航 taxonomy。
