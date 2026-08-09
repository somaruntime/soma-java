# Historical implementation qualification snapshots

状态：`HISTORICAL / SUPERSEDED AS CURRENT EXECUTABLE EVIDENCE`

本目录保存 I2–I4 实施时点的 consumer、generated-source/ABI golden、regeneration fixture 与
qualification composition。它们在当时对应 implementation commit 上形成过有效证据，但后续
I5–I7 additive generated surface 已使其中的 point-in-time golden 不再代表当前 API。

这些文件被保留是为了 provenance 和 failure analysis，不是当前 qualification：

- 不从 `scripts/`、CI 或 `build-support/` 调用；
- 不允许其 golden 覆盖当前 generated surface；
- 不为使历史 snapshot 重新通过而增加 compatibility API 或修改 production code；
- 当前 executable fact 由 module `src/test`、`tests/` 中仍在使用的 capability fixture、三个
  reference application、`scripts/check.sh` 与正式 Conformance 共同拥有。

I1 slice-only fixture 因依赖已替换的 internal constructor，已直接退役；其实施结论与原始 bytes
仍可通过 Git history 和 I1 Conformance 追溯。
