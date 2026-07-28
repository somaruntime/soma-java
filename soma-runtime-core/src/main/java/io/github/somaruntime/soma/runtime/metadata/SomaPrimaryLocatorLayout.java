package io.github.somaruntime.soma.runtime.metadata;

/**
 * 主定位器实际采用的物理后端。
 *
 * <p>V1 只暴露已验证的紧凑平面基线。Table columns 使用分段布局并不意味着主定位器
 * 可以自动分段；分段定位器必须先取得独立 production evidence，并使用新的 Plan
 * formula identity 才能启用。</p>
 */
public enum SomaPrimaryLocatorLayout {
    NONE,
    FLAT_COMPACT
}
