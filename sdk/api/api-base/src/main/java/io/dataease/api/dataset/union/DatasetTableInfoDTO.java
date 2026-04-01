package io.dataease.api.dataset.union;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author gin
 * Table/sql info for union dataset node. Callers must null-check before use, or use getTableSafe/getSqlSafe.
 */
@Data
public class DatasetTableInfoDTO implements Serializable {
    private String table;
    private String sql;

    /**
     * Null-safe getter to avoid NPE when building union dataset details.
     * Use this when tableInfoDTO may be null (e.g. legacy data or incomplete node).
     */
    public static String getTableSafe(DatasetTableInfoDTO tableInfoDTO) {
        return tableInfoDTO == null ? null : tableInfoDTO.getTable();
    }

    /**
     * Null-safe getter to avoid NPE when building union dataset details.
     */
    public static String getSqlSafe(DatasetTableInfoDTO tableInfoDTO) {
        return tableInfoDTO == null ? null : tableInfoDTO.getSql();
    }
}
