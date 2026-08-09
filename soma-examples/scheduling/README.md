# Scheduling reference application

该项目把 `Job`、`MachineState` 与 `ProcessingOption` 建模为同一 `SomaGroup` 中的普通 Table。
Option 通过 endpoint ID 和双向 Index 表达关系；application 负责候选评分、跨 Table 发布顺序和补偿。

主流程：reserve/add → typed Join 与 predicate pushdown → detached decision → Machine point update →
Job point update；第二步失败时由 application 恢复 Machine 的旧值。SOMA 不伪装提供跨 Table transaction。
