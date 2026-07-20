# Reports 导航

类型：Report 入口

状态：候选

Owner：SOMA Java 正式输出

事实范围：候选体系中的用户、开发者、性能、治理和 release 输出分类及正式物理入口映射

非事实范围：独立发明系统能力或替代 Design/代码事实

最后审查日期：2026-07-20

Report 必须声明受众、输入事实源、适用版本或 commit，并区分当前能力、目标能力和已知差距。

## 当前候选输出

- 用户：[Java 8 使用入门](user/java-8-getting-started.md)
- 开发者：[开发与维护指南](developer/development-guide.md)
- 性能：[当前性能摘要](performance/current-performance-summary.md)
- 治理：[候选文档体系评估](governance/candidate-documentation-system-report.md)
- Release：[当前 readiness 摘要](release/current-readiness.md)

这些文件用于验证新分类，尚未替代当前 `guides/` 或 `reports/`。正式切换时不创建 `docs/reports/`：用户/开发者内容合并到 `guides/`，性能、治理、Gate 和 release snapshot合并或链接到 `reports/`；已有历史报告保留原路径和日期，不因文档体系切换重写当时结论。

Current 导航只列仍适用于当前版本的输出；失去当前性的报告进入 superseded/archive 区域或明确标注历史输入。持续更新的指南声明适用版本，快照报告声明日期、commit、环境和方法。
