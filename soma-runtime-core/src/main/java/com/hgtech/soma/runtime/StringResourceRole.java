package com.hgtech.soma.runtime;

/** String field 在同时存活 workload 中承担的 SOMA 角色。 */
public enum StringResourceRole {
    PAYLOAD,
    KEY,
    UNIQUE,
    INDEX,
    GROUP,
    JOIN
}
