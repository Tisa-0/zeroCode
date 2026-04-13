package io.dataease.dataset.manage;

import io.dataease.api.dataset.union.*;
import io.dataease.api.permissions.auth.dto.BusiPerCheckDTO;
import io.dataease.commons.utils.SqlparserUtils;
import io.dataease.constant.AuthEnum;
import io.dataease.dataset.constant.DatasetTableType;
import io.dataease.dataset.utils.DatasetTableTypeConstants;
import io.dataease.dataset.utils.SqlUtils;
import io.dataease.dataset.utils.TableUtils;
import io.dataease.datasource.dao.auto.entity.CoreDatasource;
import io.dataease.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.dataease.datasource.manage.EngineManage;
import io.dataease.engine.constant.ExtFieldConstant;
import io.dataease.engine.constant.SQLConstants;
import io.dataease.exception.DEException;
import io.dataease.extensions.datasource.api.PluginManageApi;
import io.dataease.extensions.datasource.dto.DatasetTableDTO;
import io.dataease.extensions.datasource.dto.DatasetTableFieldDTO;
import io.dataease.extensions.datasource.dto.DatasourceSchemaDTO;
import io.dataease.extensions.datasource.dto.DsTypeDTO;
import io.dataease.extensions.datasource.model.SQLObj;
import io.dataease.extensions.datasource.vo.DatasourceConfiguration;
import io.dataease.extensions.datasource.vo.XpackPluginsDatasourceVO;
import io.dataease.extensions.view.dto.ChartExtFilterDTO;
import io.dataease.extensions.view.dto.ChartExtRequest;
import io.dataease.extensions.view.dto.SqlVariableDetails;
import io.dataease.i18n.Translator;
import io.dataease.independent.arrange.service.ArrangeJoinSqlService;
import io.dataease.license.utils.LicenseUtil;
import io.dataease.system.manage.CorePermissionManage;
import io.dataease.utils.BeanUtils;
import io.dataease.utils.JsonUtil;
import javax.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author Junjun
 */
@Component
public class DatasetSQLManage {

    @Resource
    private CoreDatasourceMapper coreDatasourceMapper;
    @Resource
    private EngineManage engineManage;

    @Resource
    private CorePermissionManage corePermissionManage;
    @Resource
    private ArrangeJoinSqlService arrangeJoinSqlService;

    @Autowired(required = false)
    private PluginManageApi pluginManage;

    private static Logger logger = LoggerFactory.getLogger(DatasetSQLManage.class);

    private static final List<String> NOT_FULL_DS = Arrays.asList("mysql", "mariadb", "Excel", "API");

    private List<SqlVariableDetails> filterParameters(ChartExtRequest chartExtRequest, Long datasetTableId) {
        List<SqlVariableDetails> parameters = new ArrayList<>();
        if (chartExtRequest != null && ObjectUtils.isNotEmpty(chartExtRequest.getFilter())) {
            for (ChartExtFilterDTO filterDTO : chartExtRequest.getFilter()) {
                if (CollectionUtils.isEmpty(filterDTO.getValue())) {
                    continue;
                }
                if (ObjectUtils.isNotEmpty(filterDTO.getParameters())) {
                    for (SqlVariableDetails parameter : filterDTO.getParameters()) {
                        if (parameter.getDatasetTableId().equals(datasetTableId)) {
                            parameter.setValue(filterDTO.getValue());
                            parameter.setOperator(filterDTO.getOperator());
                            parameters.add(parameter);
                        }
                    }
                }
            }
        }
        return parameters;
    }

    public Map<String, Object> getUnionSQLForEdit(DatasetGroupInfoDTO dataTableInfoDTO, ChartExtRequest chartExtRequest) throws Exception {
        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        List<UnionDTO> union = dataTableInfoDTO.getUnion();
        // 所有选中的字段，即select后的查询字段
        Map<String, String[]> checkedInfo = new LinkedHashMap<>();
        List<UnionParamDTO> unionList = new ArrayList<>();
        List<DatasetTableFieldDTO> checkedFields = new ArrayList<>();
        String sql = "";

        if (ObjectUtils.isEmpty(union)) {
            return null;
        }

        DatasetTableDTO currentDs = union.get(0).getCurrentDs();
        if (currentDs == null) {
            DEException.throwException("联合数据集的第一个节点(currentDs)为空");
        }

        // get datasource and schema,put map
        String tableSchema = putObj2Map(dsMap, currentDs);
        // get table
        DatasetTableInfoDTO infoDTO = StringUtils.isNotEmpty(currentDs.getInfo())
                ? JsonUtil.parseObject(currentDs.getInfo(), DatasetTableInfoDTO.class) : null;
        Set<Long> allDs = getAllDs(union);
        boolean isCross = allDs.size() > 1;

        SQLObj tableName = getUnionTable(currentDs, infoDTO, tableSchema, 0, filterParameters(chartExtRequest, currentDs.getId()), chartExtRequest == null, isCross, dsMap);

        for (int i = 0; i < union.size(); i++) {
            UnionDTO unionDTO = union.get(i);
            DatasetTableDTO datasetTable = unionDTO.getCurrentDs();
            if (datasetTable == null) {
                logger.warn("union[{}] currentDs is null, skip", i);
                continue;
            }
            DatasetTableInfoDTO tableInfo = StringUtils.isNotEmpty(datasetTable.getInfo())
                    ? JsonUtil.parseObject(datasetTable.getInfo(), DatasetTableInfoDTO.class) : null;

            String schema;
            if (dsMap.containsKey(datasetTable.getDatasourceId())) {
                schema = dsMap.get(datasetTable.getDatasourceId()).getSchemaAlias();
            } else {
                schema = putObj2Map(dsMap, datasetTable);
            }
            SQLObj table = getUnionTable(datasetTable, tableInfo, schema, i, filterParameters(chartExtRequest, currentDs.getId()), chartExtRequest == null, isCross, dsMap);

            // 获取前端传过来选中的字段
            List<DatasetTableFieldDTO> fields = unionDTO.getCurrentDsFields();
            fields = fields.stream().filter(DatasetTableFieldDTO::getChecked).collect(Collectors.toList());

            String[] array = fields.stream()
                    .map(f -> {
                        String alias;
                        if (StringUtils.isEmpty(f.getDataeaseName())) {
                            alias = TableUtils.fieldNameShort(table.getTableAlias() + "_" + f.getOriginName());
                        } else {
                            alias = f.getDataeaseName();
                        }

                        f.setFieldShortName(alias);
                        f.setDataeaseName(f.getFieldShortName());
                        f.setDatasetTableId(datasetTable.getId());
                        String prefix = "";
                        String suffix = "";
                        if (Objects.equals(f.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                            if (isCross) {
                                prefix = "`";
                                suffix = "`";
                            } else {
                                DsTypeDTO datasourceType = getDatasourceType(dsMap, datasetTable.getDatasourceId());
                                prefix = datasourceType.getPrefix();
                                suffix = datasourceType.getSuffix();
                            }
                        }
                        return table.getTableAlias() + "." + prefix + f.getOriginName() + suffix + " AS " + prefix + alias + suffix;
                    })
                    .toArray(String[]::new);
            checkedInfo.put(table.getTableAlias(), array);
            checkedFields.addAll(fields);
            // 获取child的fields和union
            if (!CollectionUtils.isEmpty(unionDTO.getChildrenDs())) {
                getUnionForEdit(datasetTable, table, unionDTO.getChildrenDs(), checkedInfo, unionList, checkedFields, dsMap, chartExtRequest, isCross);
            }
        }
        // build sql
        boolean isFullJoin = false;
        if (!CollectionUtils.isEmpty(unionList)) {
            // field
            StringBuilder field = new StringBuilder();
            for (Map.Entry<String, String[]> next : checkedInfo.entrySet()) {
                if (next.getValue().length > 0) {
                    field.append(StringUtils.join(next.getValue(), ",")).append(",");
                }
            }
            String f = subPrefixSuffixChar(field.toString());
            // join
            StringBuilder join = new StringBuilder();
            for (UnionParamDTO unionParamDTO : unionList) {
                // get join type
                String joinType = convertUnionTypeToSQL(unionParamDTO.getUnionType());
                // 如果不是全连接则需要校验连接方式
                if (!isFullJoin) {
                    if (StringUtils.equalsIgnoreCase(unionParamDTO.getUnionType(), "full")) {
                        isFullJoin = true;
                    }
                }

                SQLObj parentSQLObj = unionParamDTO.getParentSQLObj();
                SQLObj currentSQLObj = unionParamDTO.getCurrentSQLObj();
                DatasetTableDTO parentDs = unionParamDTO.getParentDs();
                DatasetTableDTO currentDs1 = unionParamDTO.getCurrentDs();

                String ts = "";
                String tablePrefix = "";
                String tableSuffix = "";
                if (ObjectUtils.isNotEmpty(currentSQLObj.getTableSchema())) {
                    ts = currentSQLObj.getTableSchema() + ".";

                    if (isCross) {
                        tablePrefix = "`";
                        tableSuffix = "`";
                    } else {
                        DsTypeDTO datasourceType = getDatasourceType(dsMap, currentDs1.getDatasourceId());
                        tablePrefix = datasourceType.getPrefix();
                        tableSuffix = datasourceType.getSuffix();
                    }
                }
                // build join
                join.append(" ").append(joinType).append(" ")
                        .append(ts)
                        .append(tablePrefix + currentSQLObj.getTableName() + tableSuffix)
                        .append(" ").append(currentSQLObj.getTableAlias()).append(" ")
                        .append(" ON ");
                if (unionParamDTO.getUnionFields().size() == 0) {
                    DEException.throwException(Translator.get("i18n_union_field_can_not_empty"));
                }
                for (int i = 0; i < unionParamDTO.getUnionFields().size(); i++) {
                    UnionItemDTO unionItemDTO = unionParamDTO.getUnionFields().get(i);
                    // 通过field id取得field详情，并且以第一组为准，寻找dataset table
                    DatasetTableFieldDTO parentField = unionItemDTO.getParentField();
                    DatasetTableFieldDTO currentField = unionItemDTO.getCurrentField();
                    String pPrefix = "";
                    String pSuffix = "";
                    if (Objects.equals(parentField.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                        if (isCross) {
                            pPrefix = "`";
                            pSuffix = "`";
                        } else {
                            DsTypeDTO datasourceType = getDatasourceType(dsMap, parentDs.getDatasourceId());
                            pPrefix = datasourceType.getPrefix();
                            pSuffix = datasourceType.getSuffix();
                        }
                    }
                    String cPrefix = "";
                    String cSuffix = "";
                    if (Objects.equals(currentField.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                        if (isCross) {
                            cPrefix = "`";
                            cSuffix = "`";
                        } else {
                            DsTypeDTO datasourceType = getDatasourceType(dsMap, currentDs1.getDatasourceId());
                            cPrefix = datasourceType.getPrefix();
                            cSuffix = datasourceType.getSuffix();
                        }
                    }
                    join.append(parentSQLObj.getTableAlias()).append(".")
                            .append(pPrefix + parentField.getOriginName() + pSuffix)
                            .append(" = ")
                            .append(currentSQLObj.getTableAlias()).append(".")
                            .append(cPrefix + currentField.getOriginName() + cSuffix);
                    if (i < unionParamDTO.getUnionFields().size() - 1) {
                        join.append(" AND ");
                    }
                }
            }
            if (StringUtils.isEmpty(f)) {
                DEException.throwException(Translator.get("i18n_union_ds_no_checked"));
            }
            sql = MessageFormat.format("SELECT {0} FROM {1}", f, TableUtils.getTableAndAlias(tableName, getDatasourceType(dsMap, currentDs.getDatasourceId()), isCross)) + join.toString();
        } else {
            String f = StringUtils.join(checkedInfo.get(tableName.getTableAlias()), ",");
            if (StringUtils.isEmpty(f)) {
                DEException.throwException(Translator.get("i18n_union_ds_no_checked"));
            }
            sql = MessageFormat.format("SELECT {0} FROM {1}", f, TableUtils.getTableAndAlias(tableName, getDatasourceType(dsMap, currentDs.getDatasourceId()), isCross));
        }
        // MySQL 等不支持 FULL JOIN 的数据源：用 LEFT UNION RIGHT 模拟外连接
        boolean fullJoinSimulated = false;
        if (isFullJoin && !CollectionUtils.isEmpty(unionList) && !CollectionUtils.isEmpty(dsMap)) {
            String dsType = dsMap.values().iterator().next().getType();
            if (dsType != null && NOT_FULL_DS.stream().anyMatch(t -> t.equalsIgnoreCase(dsType))) {
                sql = buildFullJoinSimulationSQL(unionList, checkedInfo, tableName, dsMap, currentDs, isCross);
                fullJoinSimulated = true;
            }
        }

        logger.info("calcite origin sql: " + sql);
        Map<String, Object> map = new HashMap<>();
        map.put("sql", sql);
        map.put("field", checkedFields);
        map.put("join", unionList);
        map.put("dsMap", dsMap);
        map.put("isFullJoin", isFullJoin);
        map.put("fullJoinSimulated", fullJoinSimulated);
        return map;
    }

    /**
     * 为不支持 FULL JOIN 的数据源（如 MySQL）生成模拟 SQL：(LEFT JOIN) UNION (RIGHT JOIN WHERE parent IS NULL)
     */
    private String buildFullJoinSimulationSQL(List<UnionParamDTO> unionList,
                                              Map<String, String[]> checkedInfo,
                                              SQLObj tableName,
                                              Map<Long, DatasourceSchemaDTO> dsMap,
                                              DatasetTableDTO currentDs,
                                              boolean isCross) {
        StringBuilder field = new StringBuilder();
        for (Map.Entry<String, String[]> next : checkedInfo.entrySet()) {
            if (next.getValue().length > 0) {
                field.append(StringUtils.join(next.getValue(), ",")).append(",");
            }
        }
        String f = subPrefixSuffixChar(field.toString());
        String baseFrom = MessageFormat.format("SELECT {0} FROM {1}", f,
                TableUtils.getTableAndAlias(tableName, getDatasourceType(dsMap, currentDs.getDatasourceId()), isCross));

        StringBuilder joinLeft = new StringBuilder();
        StringBuilder joinRight = new StringBuilder();
        List<String> whereParts = new ArrayList<>();

        for (UnionParamDTO unionParamDTO : unionList) {
            String joinType = convertUnionTypeToSQL(unionParamDTO.getUnionType());
            String joinTypeLeft = "full".equalsIgnoreCase(unionParamDTO.getUnionType()) ? " LEFT JOIN " : joinType;
            String joinTypeRight = "full".equalsIgnoreCase(unionParamDTO.getUnionType()) ? " RIGHT JOIN " : joinType;

            String oneJoin = buildOneJoinClause(unionParamDTO, dsMap, isCross);
            joinLeft.append(joinTypeLeft).append(oneJoin);
            joinRight.append(joinTypeRight).append(oneJoin);

            if ("full".equalsIgnoreCase(unionParamDTO.getUnionType())) {
                String part = buildWhereParentNull(unionParamDTO, dsMap, isCross);
                if (StringUtils.isNotEmpty(part)) {
                    whereParts.add(part);
                }
            }
        }

        String sqlLeft = baseFrom + joinLeft.toString();
        String sqlRight = baseFrom + joinRight.toString();
        if (!whereParts.isEmpty()) {
            sqlRight = sqlRight + " WHERE " + String.join(" AND ", whereParts);
        }
        return "(" + sqlLeft + ") UNION (" + sqlRight + ")";
    }

    private String buildOneJoinClause(UnionParamDTO unionParamDTO, Map<Long, DatasourceSchemaDTO> dsMap, boolean isCross) {
        SQLObj parentSQLObj = unionParamDTO.getParentSQLObj();
        SQLObj currentSQLObj = unionParamDTO.getCurrentSQLObj();
        DatasetTableDTO parentDs = unionParamDTO.getParentDs();
        DatasetTableDTO currentDs1 = unionParamDTO.getCurrentDs();

        String ts = "";
        String tablePrefix = "";
        String tableSuffix = "";
        if (ObjectUtils.isNotEmpty(currentSQLObj.getTableSchema())) {
            ts = currentSQLObj.getTableSchema() + ".";
            if (isCross) {
                tablePrefix = "`";
                tableSuffix = "`";
            } else {
                DsTypeDTO datasourceType = getDatasourceType(dsMap, currentDs1.getDatasourceId());
                tablePrefix = datasourceType.getPrefix();
                tableSuffix = datasourceType.getSuffix();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ts).append(tablePrefix).append(currentSQLObj.getTableName()).append(tableSuffix)
                .append(" ").append(currentSQLObj.getTableAlias()).append(" ON ");
        for (int i = 0; i < unionParamDTO.getUnionFields().size(); i++) {
            UnionItemDTO unionItemDTO = unionParamDTO.getUnionFields().get(i);
            DatasetTableFieldDTO parentField = unionItemDTO.getParentField();
            DatasetTableFieldDTO currentField = unionItemDTO.getCurrentField();
            if (parentField == null || currentField == null
                    || StringUtils.isEmpty(parentField.getOriginName())
                    || StringUtils.isEmpty(currentField.getOriginName())) {
                DEException.throwException(Translator.get("i18n_union_field_can_not_empty"));
            }
            String pPrefix = "", pSuffix = "";
            if (Objects.equals(parentField.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                if (isCross) {
                    pPrefix = "`";
                    pSuffix = "`";
                } else {
                    DsTypeDTO dt = getDatasourceType(dsMap, parentDs.getDatasourceId());
                    pPrefix = dt.getPrefix();
                    pSuffix = dt.getSuffix();
                }
            }
            String cPrefix = "", cSuffix = "";
            if (Objects.equals(currentField.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                if (isCross) {
                    cPrefix = "`";
                    cSuffix = "`";
                } else {
                    DsTypeDTO dt = getDatasourceType(dsMap, currentDs1.getDatasourceId());
                    cPrefix = dt.getPrefix();
                    cSuffix = dt.getSuffix();
                }
            }
            sb.append(parentSQLObj.getTableAlias()).append(".")
                    .append(pPrefix).append(parentField.getOriginName()).append(pSuffix)
                    .append(" = ")
                    .append(currentSQLObj.getTableAlias()).append(".")
                    .append(cPrefix).append(currentField.getOriginName()).append(cSuffix);
            if (i < unionParamDTO.getUnionFields().size() - 1) {
                sb.append(" AND ");
            }
        }
        return sb.toString();
    }

    private String buildWhereParentNull(UnionParamDTO unionParamDTO, Map<Long, DatasourceSchemaDTO> dsMap, boolean isCross) {
        if (CollectionUtils.isEmpty(unionParamDTO.getUnionFields())) {
            return "";
        }
        DatasetTableDTO parentDs = unionParamDTO.getParentDs();
        SQLObj parentSQLObj = unionParamDTO.getParentSQLObj();
        List<String> conditions = new ArrayList<>();
        for (UnionItemDTO unionItemDTO : unionParamDTO.getUnionFields()) {
            DatasetTableFieldDTO parentField = unionItemDTO.getParentField();
            if (parentField == null || StringUtils.isEmpty(parentField.getOriginName())) {
                continue;
            }
            String pPrefix = "", pSuffix = "";
            if (Objects.equals(parentField.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                if (isCross) {
                    pPrefix = "`";
                    pSuffix = "`";
                } else {
                    DsTypeDTO dt = getDatasourceType(dsMap, parentDs.getDatasourceId());
                    pPrefix = dt.getPrefix();
                    pSuffix = dt.getSuffix();
                }
            }
            conditions.add(parentSQLObj.getTableAlias() + "." + pPrefix + parentField.getOriginName() + pSuffix + " IS NULL");
        }
        return String.join(" AND ", conditions);
    }

    // 递归计算出所有子级的checkedFields和unionParam
    private void getUnionForEdit(DatasetTableDTO parentTable, SQLObj parentSQLObj,
                                 List<UnionDTO> childrenDs, Map<String, String[]> checkedInfo,
                                 List<UnionParamDTO> unionList, List<DatasetTableFieldDTO> checkedFields,
                                 Map<Long, DatasourceSchemaDTO> dsMap, ChartExtRequest chartExtRequest,
                                 boolean isCross) throws Exception {
        for (int i = 0; i < childrenDs.size(); i++) {
            int index = unionList.size() + 1;

            UnionDTO unionDTO = childrenDs.get(i);
            DatasetTableDTO datasetTable = unionDTO.getCurrentDs();
            if (datasetTable == null) {
                logger.warn("childrenDs[{}] currentDs is null, skip", i);
                continue;
            }
            DatasetTableInfoDTO tableInfo = StringUtils.isNotEmpty(datasetTable.getInfo())
                    ? JsonUtil.parseObject(datasetTable.getInfo(), DatasetTableInfoDTO.class) : null;

            String schema;
            if (dsMap.containsKey(datasetTable.getDatasourceId())) {
                schema = dsMap.get(datasetTable.getDatasourceId()).getSchemaAlias();
            } else {
                schema = putObj2Map(dsMap, datasetTable);
            }
            SQLObj table = getUnionTable(datasetTable, tableInfo, schema, index, filterParameters(chartExtRequest, datasetTable.getId()), chartExtRequest == null, isCross, dsMap);

            List<DatasetTableFieldDTO> fields = unionDTO.getCurrentDsFields();
            fields = fields.stream().filter(DatasetTableFieldDTO::getChecked).collect(Collectors.toList());

            String[] array = fields.stream()
                    .map(f -> {
                        String alias;
                        if (StringUtils.isEmpty(f.getDataeaseName())) {
                            alias = TableUtils.fieldNameShort(table.getTableAlias() + "_" + f.getOriginName());
                        } else {
                            alias = f.getDataeaseName();
                        }

                        f.setFieldShortName(alias);
                        f.setDataeaseName(f.getFieldShortName());
                        f.setDatasetTableId(datasetTable.getId());
                        String prefix = "";
                        String suffix = "";
                        if (Objects.equals(f.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                            if (isCross) {
                                prefix = "`";
                                suffix = "`";
                            } else {
                                DsTypeDTO datasourceType = getDatasourceType(dsMap, datasetTable.getDatasourceId());
                                prefix = datasourceType.getPrefix();
                                suffix = datasourceType.getSuffix();
                            }
                        }
                        return table.getTableAlias() + "." + prefix + f.getOriginName() + suffix + " AS " + prefix + alias + suffix;
                    })
                    .toArray(String[]::new);
            checkedInfo.put(table.getTableAlias(), array);
            checkedFields.addAll(fields);

            UnionParamDTO unionToParent = unionDTO.getUnionToParent();
            // 设置关联关系中，两个table信息
            unionToParent.setParentDs(parentTable);
            unionToParent.setParentSQLObj(parentSQLObj);
            unionToParent.setCurrentDs(datasetTable);
            unionToParent.setCurrentSQLObj(table);
            unionList.add(unionToParent);
            if (!CollectionUtils.isEmpty(unionDTO.getChildrenDs())) {
                getUnionForEdit(datasetTable, table, unionDTO.getChildrenDs(), checkedInfo, unionList, checkedFields, dsMap, chartExtRequest, isCross);
            }
        }
    }

    private Set<Long> getAllDs(List<UnionDTO> union) {
        Set<Long> set = new HashSet<>();
        for (UnionDTO unionDTO : union) {
            if (unionDTO.getCurrentDs() != null && unionDTO.getCurrentDs().getDatasourceId() != null) {
                set.add(unionDTO.getCurrentDs().getDatasourceId());
            }
            if (!CollectionUtils.isEmpty(unionDTO.getChildrenDs())) {
                getChildrenDs(unionDTO.getChildrenDs(), set);
            }
        }
        return set;
    }

    private void getChildrenDs(List<UnionDTO> childrenDs, Set<Long> set) {
        for (UnionDTO unionDTO : childrenDs) {
            if (unionDTO.getCurrentDs() != null && unionDTO.getCurrentDs().getDatasourceId() != null) {
                set.add(unionDTO.getCurrentDs().getDatasourceId());
            }
            if (!CollectionUtils.isEmpty(unionDTO.getChildrenDs())) {
                getChildrenDs(unionDTO.getChildrenDs(), set);
            }
        }
    }

    private DsTypeDTO getDatasourceType(Map<Long, DatasourceSchemaDTO> dsMap, Long datasourceId) {
        DatasourceSchemaDTO datasourceSchemaDTO = dsMap.get(datasourceId);
        String type;
        if (datasourceSchemaDTO == null) {
            CoreDatasource coreDatasource = coreDatasourceMapper.selectById(datasourceId);
            if (coreDatasource == null) {
                DEException.throwException(Translator.get("i18n_dataset_ds_error") + ",ID:" + datasourceId);
            }
            type = engineManage.getDeEngine().getType();
        } else {
            type = datasourceSchemaDTO.getType();
        }

        if (Arrays.stream(DatasourceConfiguration.DatasourceType.values()).map(DatasourceConfiguration.DatasourceType::getType).collect(Collectors.toList()).contains(type)) {
            DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(type);
            DsTypeDTO dto = new DsTypeDTO();
            BeanUtils.copyBean(dto, datasourceType);
            return dto;
        } else {
            if (LicenseUtil.licenseValid()) {
                List<XpackPluginsDatasourceVO> xpackPluginsDatasourceVOS = pluginManage.queryPluginDs();
                List<XpackPluginsDatasourceVO> list = xpackPluginsDatasourceVOS.stream().filter(ele -> StringUtils.equals(ele.getType(), type)).collect(Collectors.toList());
                if (ObjectUtils.isNotEmpty(list)) {
                    XpackPluginsDatasourceVO first = list.get(0);
                    DsTypeDTO dto = new DsTypeDTO();
                    dto.setName(first.getName());
                    dto.setCatalog(first.getCategory());
                    dto.setType(first.getType());
                    dto.setPrefix(first.getPrefix());
                    dto.setSuffix(first.getSuffix());
                    return dto;
                } else {
                    DEException.throwException("当前数据源插件不存在");
                }
            }
            return null;
        }
    }

    public String subPrefixSuffixChar(String str) {
        while (StringUtils.startsWith(str, ",")) {
            str = str.substring(1, str.length());
        }
        while (StringUtils.endsWith(str, ",")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    private String convertUnionTypeToSQL(String unionType) {
        return arrangeJoinSqlService.unionTypeToSqlJoin(unionType);
    }

    private SQLObj getUnionTable(DatasetTableDTO currentDs, DatasetTableInfoDTO infoDTO, String tableSchema, int index, List<SqlVariableDetails> parameters, boolean isFromDataSet, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap) {
        // 使用 null-safe 方法，避免任何路径下出现 NPE: Cannot invoke "DatasetTableInfoDTO.getTable()" because "tableInfoDTO" is null
        String tableName = DatasetTableInfoDTO.getTableSafe(infoDTO);
        String sqlEncoded = DatasetTableInfoDTO.getSqlSafe(infoDTO);
        if (tableName == null && sqlEncoded == null) {
            DEException.throwException("数据集节点的表信息为空(tableInfoDTO is null)，请检查数据集配置是否完整。数据集ID: " + (currentDs != null ? currentDs.getId() : "unknown"));
        }
        SQLObj tableObj;
        String tableAlias = String.format(SQLConstants.TABLE_ALIAS_PREFIX, index);
        if (StringUtils.equalsIgnoreCase(currentDs.getType(), DatasetTableTypeConstants.DATASET_TABLE_DB)) {
            if (StringUtils.isEmpty(tableName)) {
                DEException.throwException("DB类型数据集节点的表名为空，请检查数据集配置。数据集ID: " + (currentDs != null ? currentDs.getId() : "unknown"));
            }
            tableObj = SQLObj.builder().tableSchema(tableSchema).tableName(tableName).tableAlias(tableAlias).build();
        } else if (StringUtils.equalsIgnoreCase(currentDs.getType(), DatasetTableTypeConstants.DATASET_TABLE_SQL)) {
            if (StringUtils.isEmpty(sqlEncoded)) {
                DEException.throwException("SQL类型数据集节点的SQL为空，请检查数据集配置。数据集ID: " + (currentDs != null ? currentDs.getId() : "unknown"));
            }
            String sql = SqlparserUtils.handleVariableDefaultValue(new String(Base64.getDecoder().decode(sqlEncoded)), currentDs.getSqlVariableDetails(), false, isFromDataSet, parameters, isCross, dsMap, pluginManage);
            if (isCross) {
                sql = SqlUtils.addSchema(sql, tableSchema);
            }
            tableObj = SQLObj.builder().tableSchema("").tableName("(" + sql + ")").tableAlias(tableAlias).build();
        } else {
            if (StringUtils.isEmpty(tableName)) {
                DEException.throwException("数据集节点的表名为空，请检查数据集配置。数据集ID: " + (currentDs != null ? currentDs.getId() : "unknown"));
            }
            tableObj = SQLObj.builder().tableSchema(tableSchema).tableName(tableName).tableAlias(tableAlias).build();
        }
        return tableObj;
    }

    private String putObj2Map(Map<Long, DatasourceSchemaDTO> dsMap, DatasetTableDTO ds) throws Exception {
        // 通过datasource id校验数据源权限
        BusiPerCheckDTO dto = new BusiPerCheckDTO();
        dto.setId(ds.getDatasourceId());
        dto.setAuthEnum(AuthEnum.READ);
        boolean checked = corePermissionManage.checkAuth(dto);
        if (!checked) {
            DEException.throwException(Translator.get("i18n_no_datasource_permission"));
        }


        String schemaAlias;
        if (StringUtils.equalsIgnoreCase(ds.getType(), DatasetTableType.DB) || StringUtils.equalsIgnoreCase(ds.getType(), DatasetTableType.SQL)) {
            CoreDatasource coreDatasource = coreDatasourceMapper.selectById(ds.getDatasourceId());
            if (coreDatasource == null) {
                DEException.throwException(Translator.get("i18n_dataset_ds_error") + ",ID:" + ds.getDatasourceId());
            }
            if (StringUtils.equalsIgnoreCase("excel", coreDatasource.getType()) || StringUtils.equalsIgnoreCase("api", coreDatasource.getType())) {
                coreDatasource = engineManage.getDeEngine();
            }
            schemaAlias = String.format(SQLConstants.SCHEMA, coreDatasource.getId());
            if (!dsMap.containsKey(coreDatasource.getId())) {
                DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
                BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
                datasourceSchemaDTO.setSchemaAlias(schemaAlias);
                dsMap.put(coreDatasource.getId(), datasourceSchemaDTO);
            }
        } else {
            CoreDatasource coreDatasource = engineManage.getDeEngine();
            schemaAlias = String.format(SQLConstants.SCHEMA, coreDatasource.getId());
            if (!dsMap.containsKey(coreDatasource.getId())) {
                DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
                BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
                datasourceSchemaDTO.setSchemaAlias(schemaAlias);
                dsMap.put(coreDatasource.getId(), datasourceSchemaDTO);
            }
        }
        return schemaAlias;
    }
}
