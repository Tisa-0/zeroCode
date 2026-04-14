package io.dataease.api.dataset.union;

import io.dataease.api.dataset.dto.DeSortField;
import io.dataease.api.dataset.dto.DatasetNodeDTO;
import io.dataease.extensions.datasource.dto.DatasetTableFieldDTO;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @Author Junjun
 */
@Data
public class DatasetGroupInfoDTO extends DatasetNodeDTO {
    private List<UnionDTO> union;// 关联数据集

    private List<DeSortField> sortFields;// 自定义排序（如仪表板查询组件）

    // 画布布局信息：节点位置、连线等（仅前端使用，统一由 info 字段 JSON 持久化）
    private Map<String, Object> graphState;

    // 当前正在预览的画布节点 id（用于联合/去重/分组/抽样等操作节点预览）
    private String previewNodeId;

    private Map<String, List<?>> data;

    private List<DatasetTableFieldDTO> allFields;

    private String sql;

    private Long total;

    private String creator;

    private String updater;
}
