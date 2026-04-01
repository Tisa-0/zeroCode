package io.dataease.dataset.manage;

import io.dataease.api.chart.dto.DeSortField;
import io.dataease.api.dataset.dto.*;
import io.dataease.api.dataset.union.DatasetGroupInfoDTO;
import io.dataease.api.dataset.union.DatasetTableInfoDTO;
import io.dataease.api.permissions.dataset.dto.DataSetRowPermissionsTreeDTO;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.chart.utils.ChartDataBuild;
import io.dataease.commons.utils.SqlparserUtils;
import io.dataease.dataset.constant.DatasetTableType;
import io.dataease.dataset.utils.DatasetUtils;
import io.dataease.dataset.utils.FieldUtils;
import io.dataease.dataset.utils.TableUtils;
import io.dataease.datasource.dao.auto.entity.CoreDatasource;
import io.dataease.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.dataease.datasource.manage.EngineManage;
import io.dataease.datasource.utils.DatasourceUtils;
import io.dataease.engine.constant.ExtFieldConstant;
import io.dataease.engine.constant.SQLConstants;
import io.dataease.engine.sql.SQLProvider;
import io.dataease.engine.trans.*;
import io.dataease.engine.utils.SQLUtils;
import io.dataease.engine.utils.Utils;
import io.dataease.exception.DEException;
import io.dataease.extensions.datasource.api.PluginManageApi;
import io.dataease.extensions.datasource.constant.SqlPlaceholderConstants;
import io.dataease.extensions.datasource.dto.*;
import io.dataease.extensions.datasource.factory.ProviderFactory;
import io.dataease.extensions.datasource.model.SQLMeta;
import io.dataease.extensions.datasource.provider.Provider;
import io.dataease.extensions.view.dto.ChartExtFilterDTO;
import io.dataease.extensions.view.dto.ChartExtRequest;
import io.dataease.extensions.view.dto.ColumnPermissionItem;
import io.dataease.extensions.view.dto.SqlVariableDetails;
import io.dataease.i18n.Translator;
import io.dataease.utils.AuthUtils;
import io.dataease.utils.BeanUtils;
import io.dataease.utils.JsonUtil;
import io.dataease.utils.TreeUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static io.dataease.chart.manage.ChartDataManage.START_END_SEPARATOR;

/**
 * @Author Junjun
 */
@Component
public class DatasetDataManage {
    @Resource
    private DatasetSQLManage datasetSQLManage;
    @Resource
    private CoreDatasourceMapper coreDatasourceMapper;
    @Resource
    private DatasetTableFieldManage datasetTableFieldManage;
    @Resource
    private EngineManage engineManage;
    @Resource
    private DatasetGroupManage datasetGroupManage;
    @Resource
    private PermissionManage permissionManage;
    @Resource
    private DatasetTableSqlLogManage datasetTableSqlLogManage;
    @Autowired(required = false)
    private PluginManageApi pluginManage;

    private static Logger logger = LoggerFactory.getLogger(DatasetDataManage.class);

    private static final List<String> notFullDs = List.of("mysql", "mariadb", "Excel", "API");

    public List<DatasetTableFieldDTO> getTableFields(DatasetTableDTO datasetTableDTO) throws Exception {
        List<DatasetTableFieldDTO> list = null;
        List<TableField> tableFields = null;
        String type = datasetTableDTO.getType();
        DatasetTableInfoDTO tableInfoDTO = StringUtils.isNotEmpty(datasetTableDTO.getInfo())
                ? JsonUtil.parseObject(datasetTableDTO.getInfo(), DatasetTableInfoDTO.class) : null;
        // 使用 null-safe 方法，避免 NPE: Cannot invoke "DatasetTableInfoDTO.getTable()" because "tableInfoDTO" is null
        String tableName = DatasetTableInfoDTO.getTableSafe(tableInfoDTO);
        String sqlEncoded = DatasetTableInfoDTO.getSqlSafe(tableInfoDTO);
        if (tableName == null && sqlEncoded == null) {
            DEException.throwException("数据集节点的表信息为空(tableInfoDTO is null)，请检查数据集配置是否完整。数据集ID: " + datasetTableDTO.getId());
        }
        if (StringUtils.equalsIgnoreCase(type, DatasetTableType.DB) || StringUtils.equalsIgnoreCase(type, DatasetTableType.SQL)) {
            CoreDatasource coreDatasource = coreDatasourceMapper.selectById(datasetTableDTO.getDatasourceId());
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            if (StringUtils.equalsIgnoreCase("excel", coreDatasource.getType()) || StringUtils.equalsIgnoreCase("api", coreDatasource.getType())) {
                coreDatasource = engineManage.getDeEngine();
            }
            if (StringUtils.isNotEmpty(coreDatasource.getStatus()) && "Error".equalsIgnoreCase(coreDatasource.getStatus())) {
                DEException.throwException(Translator.get("i18n_invalid_ds"));
            }
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
            datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
            Provider provider = ProviderFactory.getProvider(coreDatasource.getType());

            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
            String sql;
            if (StringUtils.equalsIgnoreCase(type, DatasetTableType.DB)) {
                if (StringUtils.isEmpty(tableName)) {
                    DEException.throwException("DB类型数据集节点的表名为空，请检查数据集配置。数据集ID: " + datasetTableDTO.getId());
                }
                sql = TableUtils.tableName2Sql(datasourceSchemaDTO, tableName) + " LIMIT 0 OFFSET 0";
                sql = Utils.replaceSchemaAlias(sql, datasourceRequest.getDsList());
                sql = provider.transSqlDialect(sql, datasourceRequest.getDsList());
            } else {
                if (StringUtils.isEmpty(sqlEncoded)) {
                    DEException.throwException("SQL类型数据集节点的SQL为空，请检查数据集配置。数据集ID: " + datasetTableDTO.getId());
                }
                String originSql = SqlparserUtils.handleVariableDefaultValue(new String(Base64.getDecoder().decode(sqlEncoded)), datasetTableDTO.getSqlVariableDetails(), false, false, null, false, datasourceRequest.getDsList(), pluginManage);
                sql = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 0, 0);
                sql = provider.transSqlDialect(sql, datasourceRequest.getDsList());
                sql = provider.replaceTablePlaceHolder(sql, originSql);
            }
            datasourceRequest.setQuery(sql.replaceAll("\r\n", " ")
                    .replaceAll("\n", " "));
            logger.info("calcite data table field sql: " + datasourceRequest.getQuery());
            if (StringUtils.equalsIgnoreCase(type, DatasetTableType.DB)) {
                datasourceRequest.setTable(tableName);
            }

            tableFields = provider.fetchTableField(datasourceRequest);
        } else {
            if (StringUtils.isEmpty(tableName)) {
                DEException.throwException("数据集节点的表名为空，请检查数据集配置。数据集ID: " + datasetTableDTO.getId());
            }
            CoreDatasource coreDatasource = engineManage.getDeEngine();
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
            datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
            Provider provider = ProviderFactory.getDefaultProvider();

            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
            String sql = TableUtils.tableName2Sql(datasourceSchemaDTO, tableName) + " LIMIT 0 OFFSET 0";
            // replace schema alias, trans dialect
            sql = Utils.replaceSchemaAlias(sql, datasourceRequest.getDsList());
            sql = provider.transSqlDialect(sql, datasourceRequest.getDsList());
            datasourceRequest.setQuery(sql);
            logger.info("calcite data table field sql: " + datasourceRequest.getQuery());
            tableFields = provider.fetchTableField(datasourceRequest);
        }
        return transFields(tableFields, true);
    }

    public List<DatasetTableFieldDTO> transFields(List<TableField> tableFields, boolean defaultStatus) {
        return tableFields.stream().map(ele -> {
            DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
            dto.setName(StringUtils.isNotEmpty(ele.getName()) ? ele.getName() : ele.getOriginName());
            dto.setOriginName(ele.getOriginName());
            dto.setChecked(defaultStatus);
            dto.setType(ele.getType());
            int deType = FieldUtils.transType2DeType(ele.getType());
            dto.setDeExtractType(ObjectUtils.isEmpty(ele.getDeExtractType()) ? deType : ele.getDeExtractType());
            dto.setDeType(ObjectUtils.isEmpty(ele.getDeType()) ? deType : ele.getDeType());
            dto.setGroupType(FieldUtils.transDeType2DQ(dto.getDeType()));
            dto.setExtField(0);
            dto.setDescription(StringUtils.isNotEmpty(ele.getName()) ? ele.getName() : null);
            return dto;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> previewDataWithLimit(DatasetGroupInfoDTO datasetGroupInfoDTO, Integer start, Integer count, boolean checkPermission) throws Exception {
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
        String sql = (String) sqlMap.get("sql");

        // 获取allFields
        List<DatasetTableFieldDTO> fields = datasetGroupInfoDTO.getAllFields();
        if (ObjectUtils.isEmpty(fields)) {
            DEException.throwException(Translator.get("i18n_no_fields"));
        }

        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        if (checkPermission) {
            fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
            if (ObjectUtils.isEmpty(fields)) {
                DEException.throwException(Translator.get("i18n_no_column_permission"));
            }
        }

        buildFieldName(sqlMap, fields);

        // 对预览字段进行一次“有效性”过滤：
        // 仅保留 inner union 实际输出的字段（sqlMap.field 中存在的 dataeaseName），
        // 避免因 allFields 中残留了已删除/未选中的字段而生成
        // t_a_0.f_xxx 这类在内层结果集中不存在的列，导致 "Unknown column 't_a_0.f_xxx' in 'field list'"。
        Object unionFieldObj = sqlMap.get("field");
        if (unionFieldObj instanceof List) {
            List<DatasetTableFieldDTO> unionFields = (List<DatasetTableFieldDTO>) unionFieldObj;
            if (!CollectionUtils.isEmpty(unionFields)) {
                Set<String> unionAliases = unionFields.stream()
                        .map(DatasetTableFieldDTO::getDataeaseName)
                        .filter(StringUtils::isNotEmpty)
                        .collect(Collectors.toSet());
                if (!CollectionUtils.isEmpty(unionAliases)) {
                    fields = fields.stream()
                            .filter(f -> StringUtils.isNotEmpty(f.getDataeaseName())
                                    && unionAliases.contains(f.getDataeaseName()))
                            .collect(Collectors.toList());
                }
            }
        }

        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        DatasourceUtils.checkDsStatus(dsMap);
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = Utils.isCrossDs(dsMap);
        if (!crossDs) {
            boolean fullJoinSimulated = Boolean.TRUE.equals(sqlMap.get("fullJoinSimulated"));
            if (notFullDs.contains(dsMap.entrySet().iterator().next().getValue().getType())
                    && Boolean.TRUE.equals(sqlMap.get("isFullJoin"))
                    && !fullJoinSimulated) {
                DEException.throwException(Translator.get("i18n_not_full"));
            }
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }

        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
        TokenUserBO user = AuthUtils.getUser();
        if (user != null && checkPermission) {
            rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
        }

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsList.getFirst());
        }

        // 解析 sortFields：使用 buildFieldName 后与内层 union 输出一致的 dataeaseName，避免 ORDER BY 列名不存在
        List<DeSortField> resolvedSortFields = resolveSortFieldsForPreview(
                datasetGroupInfoDTO.getSortFields(), fields);

        // build query sql
        SQLMeta sqlMeta = new SQLMeta();
        Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
        Field2SQLObj.field2sqlObj(sqlMeta, fields, fields, crossDs, dsMap);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, fields, crossDs, dsMap);
        Order2SQLObj.getOrders(sqlMeta, resolvedSortFields, fields, crossDs, dsMap);
        String querySQL;
        if (start == null || count == null) {
            querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, false);
        } else {
            querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, start, count);
        }
        querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.info("calcite data preview sql: " + querySQL);

        // 通过数据源请求数据
        // 调用数据源的calcite获得data
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);

        Map<String, Object> data = provider.fetchResultField(datasourceRequest);

        Map<String, Object> map = new LinkedHashMap<>();
        // 重新构造data
        Map<String, Object> previewData = buildPreviewData(data, fields, desensitizationList);

        // 若是已保存的数据集预览，且存在画布编排信息（graphState），则根据图中的抽样操作节点
        // 对预览结果进行一次行数裁剪，保证“数据集页面预览的数据”与结果集中抽样后的数据条数一致。
        logger.info("[previewDataWithLimit] id={}, graphState isNull={}",
                datasetGroupInfoDTO.getId(), datasetGroupInfoDTO.getGraphState() == null);
        if (datasetGroupInfoDTO.getId() != null && datasetGroupInfoDTO.getGraphState() != null) {
            logger.info("[previewDataWithLimit] calling applyGraphSampleForPreview, graphState keys={}",
                    datasetGroupInfoDTO.getGraphState().keySet());
            int beforeSize = previewData.get("data") instanceof List ? ((List<?>) previewData.get("data")).size() : -1;
            applyGraphSampleForPreview(datasetGroupInfoDTO.getGraphState(), previewData);
            int afterSize = previewData.get("data") instanceof List ? ((List<?>) previewData.get("data")).size() : -1;
            logger.info("[previewDataWithLimit] rows before={}, after={}", beforeSize, afterSize);
        }

        map.put("data", previewData);
        if (ObjectUtils.isEmpty(datasetGroupInfoDTO.getId())) {
            map.put("allFields", fields);
        } else {
            List<DatasetTableFieldDTO> fieldList = datasetTableFieldManage.selectByDatasetGroupId(datasetGroupInfoDTO.getId());
            map.put("allFields", fieldList);
        }
        map.put("sql", Base64.getEncoder().encodeToString(querySQL.getBytes()));
        String replaceSql = provider.rebuildSQL(SQLProvider.createQuerySQL(sqlMeta, false, false, false), sqlMeta, crossDs, dsMap);
        Long dbTotal = getDatasetTotal(datasetGroupInfoDTO, replaceSql, null);

        Object sampledData = previewData.get("data");
        if (sampledData instanceof List) {
            int sampledSize = ((List<?>) sampledData).size();
            if (sampledSize < dbTotal) {
                map.put("total", (long) sampledSize);
            } else {
                map.put("total", dbTotal);
            }
        } else {
            map.put("total", dbTotal);
        }
        return map;
    }

    /**
     * 根据 graphState 中的抽样操作节点，对预览数据进行行数裁剪：
     * - 寻找直接连入结果集(result)的节点，沿着上游查找最近的 sample 操作节点；
     * - sampleType = count 时，保留前 N 条；
     * - sampleType = percent 时，按百分比裁剪。
     */
    @SuppressWarnings("unchecked")
    private void applyGraphSampleForPreview(Map<String, Object> graphState, Map<String, Object> previewData) {
        if (graphState == null || previewData == null) {
            logger.info("[applyGraphSampleForPreview] null input, returning");
            return;
        }
        Object nodesObj = graphState.get("nodes");
        Object edgesObj = graphState.get("edges");
        logger.info("[applyGraphSampleForPreview] nodesObj type={}, edgesObj type={}",
                nodesObj != null ? nodesObj.getClass().getSimpleName() : "null",
                edgesObj != null ? edgesObj.getClass().getSimpleName() : "null");
        if (!(nodesObj instanceof List) || !(edgesObj instanceof List)) {
            logger.warn("[applyGraphSampleForPreview] nodes/edges not List, returning");
            return;
        }
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;
        List<Map<String, Object>> edges = (List<Map<String, Object>>) edgesObj;
        if (nodes.isEmpty() || edges.isEmpty()) {
            return;
        }

        // 1) 找到结果集节点（type == 'result'），默认 id 为 'result_output'
        String resultId = "result_output";
        for (Map<String, Object> n : nodes) {
            Object type = n.get("type");
            if ("result".equals(type)) {
                Object nid = n.get("id");
                if (nid != null) {
                    resultId = String.valueOf(nid);
                    break;
                }
            }
        }

        // 2) 找到直接连入结果集的节点
        String upstreamId = null;
        for (Map<String, Object> e : edges) {
            Object target = e.get("targetId");
            if (target != null && resultId.equals(String.valueOf(target))) {
                Object src = e.get("sourceId");
                if (src != null) {
                    upstreamId = String.valueOf(src);
                    break;
                }
            }
        }
        logger.info("[applyGraphSampleForPreview] resultId={}, upstreamId={}", resultId, upstreamId);
        if (upstreamId == null) {
            logger.info("[applyGraphSampleForPreview] no upstream of result, returning");
            return;
        }

        // 3) 从直接上游开始，沿着图向上回溯，寻找最近的 sample 操作节点
        Map<String, Map<String, Object>> nodeMap = new HashMap<>();
        for (Map<String, Object> n : nodes) {
            Object nid = n.get("id");
            if (nid != null) {
                nodeMap.put(String.valueOf(nid), n);
            }
        }
        Map<String, List<String>> incomingMap = new HashMap<>();
        for (Map<String, Object> e : edges) {
            Object src = e.get("sourceId");
            Object tgt = e.get("targetId");
            if (src == null || tgt == null) continue;
            String t = String.valueOf(tgt);
            incomingMap.computeIfAbsent(t, k -> new ArrayList<>()).add(String.valueOf(src));
        }

        String sampleNodeId = null;
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visitedIds = new HashSet<>();
        queue.add(upstreamId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visitedIds.add(current)) {
                continue;
            }
            Map<String, Object> n = nodeMap.get(current);
            if (n == null) continue;
            Object type = n.get("type");
            Object opType = n.get("operationType");
            if ("operation".equals(type) && "sample".equals(opType)) {
                sampleNodeId = current;
                break;
            }
            List<String> incomings = incomingMap.get(current);
            if (incomings != null) {
                queue.addAll(incomings);
            }
        }

        logger.info("[applyGraphSampleForPreview] sampleNodeId={}", sampleNodeId);
        if (sampleNodeId == null) {
            logger.info("[applyGraphSampleForPreview] no sample node found upstream, returning");
            return;
        }

        Map<String, Object> sampleNode = nodeMap.get(sampleNodeId);
        if (sampleNode == null) {
            return;
        }
        Object cfgObj = sampleNode.get("operationConfig");
        logger.info("[applyGraphSampleForPreview] sampleNode keys={}, cfgObj type={}",
                sampleNode.keySet(), cfgObj != null ? cfgObj.getClass().getSimpleName() : "null");
        if (!(cfgObj instanceof Map)) {
            return;
        }
        Map<String, Object> cfg = (Map<String, Object>) cfgObj;
        String sampleType = cfg.get("sampleType") != null ? String.valueOf(cfg.get("sampleType")) : "count";

        Object dataListObj = previewData.get("data");
        if (!(dataListObj instanceof List)) {
            return;
        }
        List<Map<String, Object>> rows = (List<Map<String, Object>>) dataListObj;
        if (rows.isEmpty()) {
            return;
        }

        int limit = rows.size();
        if ("count".equals(sampleType)) {
            Object cntObj = cfg.get("sampleCount");
            if (cntObj != null) {
                try {
                    int cnt = Integer.parseInt(String.valueOf(cntObj));
                    if (cnt > 0) {
                        limit = Math.min(cnt, rows.size());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } else if ("percent".equals(sampleType)) {
            Object pctObj = cfg.get("samplePercent");
            if (pctObj != null) {
                try {
                    double pct = Double.parseDouble(String.valueOf(pctObj));
                    if (pct > 0) {
                        int cnt = (int) Math.floor(rows.size() * pct / 100.0);
                        if (cnt <= 0) {
                            cnt = 1;
                        }
                        limit = Math.min(cnt, rows.size());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        logger.info("[applyGraphSampleForPreview] sampleType={}, limit={}, totalRows={}", sampleType, limit, rows.size());
        if (limit < rows.size()) {
            previewData.put("data", new ArrayList<>(rows.subList(0, limit)));
            logger.info("[applyGraphSampleForPreview] truncated rows from {} to {}", rows.size(), limit);
        }
    }

    public Long getDatasetTotal(Long datasetGroupId) throws Exception {
        DatasetGroupInfoDTO dto = datasetGroupManage.getForCount(datasetGroupId);
        if (StringUtils.equalsIgnoreCase(dto.getNodeType(), "dataset")) {
            return getDatasetTotal(dto, null, new ChartExtRequest());
        }
        return 0L;
    }

    public Long getDatasetTotal(DatasetGroupInfoDTO datasetGroupInfoDTO, String s, ChartExtRequest request) throws Exception {
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, request);
        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        boolean crossDs = Utils.isCrossDs(dsMap);
        String sql;
        if (StringUtils.isEmpty(s)) {
            sql = (String) sqlMap.get("sql");
            if (!crossDs) {
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }
        } else {
            sql = s;
        }
        String querySQL = "SELECT COUNT(*) FROM (" + sql + ") t_a_0";
        logger.info("calcite data count sql: " + querySQL);

        // 通过数据源请求数据
        // 调用数据源的calcite获得data
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsMap.entrySet().iterator().next().getValue().getType());
        }
        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        List<String[]> dataList = (List<String[]>) data.get("data");
        if (ObjectUtils.isNotEmpty(dataList) && ObjectUtils.isNotEmpty(dataList.get(0)) && ObjectUtils.isNotEmpty(dataList.get(0)[0])) {
            return Long.valueOf(dataList.get(0)[0]);
        }
        return 0L;
    }

    public Map<String, Object> previewSqlWithLog(PreviewSqlDTO dto) {
        if (dto == null) {
            return null;
        }
        SqlLogDTO sqlLogDTO = new SqlLogDTO();
        String sql = new String(Base64.getDecoder().decode(dto.getSql()));
        sqlLogDTO.setSql(sql);
        Map<String, Object> map = null;
        try {
            sqlLogDTO.setStartTime(System.currentTimeMillis());
            map = previewSql(dto);
            sqlLogDTO.setEndTime(System.currentTimeMillis());
            sqlLogDTO.setSpend(sqlLogDTO.getEndTime() - sqlLogDTO.getStartTime());
            sqlLogDTO.setStatus("Completed");
        } catch (Exception e) {
            sqlLogDTO.setStatus("Error");
            DEException.throwException(e.getMessage());
        } finally {
            if (ObjectUtils.isNotEmpty(dto.getTableId())) {
                sqlLogDTO.setTableId(dto.getTableId());
                datasetTableSqlLogManage.save(sqlLogDTO);
            }
        }
        return map;
    }

    public Map<String, Object> previewSql(PreviewSqlDTO dto) throws DEException {
        CoreDatasource coreDatasource = coreDatasourceMapper.selectById(dto.getDatasourceId());
        DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
        if (coreDatasource.getType().equalsIgnoreCase("API") || coreDatasource.getType().equalsIgnoreCase("Excel")) {
            BeanUtils.copyBean(datasourceSchemaDTO, engineManage.getDeEngine());
        } else {
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
        }

        if (StringUtils.isNotEmpty(datasourceSchemaDTO.getStatus()) && "Error".equalsIgnoreCase(datasourceSchemaDTO.getStatus())) {
            DEException.throwException(Translator.get("i18n_invalid_ds"));
        }

        String alias = String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId());
        datasourceSchemaDTO.setSchemaAlias(alias);

        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        dsMap.put(datasourceSchemaDTO.getId(), datasourceSchemaDTO);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(dsMap);

        // parser sql params and replace default value
        String originSql = SqlparserUtils.handleVariableDefaultValue(datasetSQLManage.subPrefixSuffixChar(new String(Base64.getDecoder().decode(dto.getSql()))), dto.getSqlVariableDetails(), true, true, null, false, dsMap, pluginManage);

        // sql 作为临时表，外层加上limit
        String sql;
        Provider provider = ProviderFactory.getProvider(datasourceSchemaDTO.getType());
        if (Utils.isNeedOrder(List.of(datasourceSchemaDTO.getType()))) {
            // 先根据sql获取表字段
            String sqlField = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 0, 0);

            sqlField = provider.transSqlDialect(sqlField, datasourceRequest.getDsList());
            // replace placeholder
            sqlField = provider.replaceTablePlaceHolder(sqlField, originSql);
            datasourceRequest.setQuery(sqlField);

            // 获取数据源表的原始字段
            List<TableField> list = provider.fetchTableField(datasourceRequest);
            if (ObjectUtils.isEmpty(list)) {
                return null;
            }
            sql = SQLUtils.buildOriginPreviewSqlWithOrderBy(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 100, 0, String.format(SQLConstants.FIELD_DOT, list.get(0).getOriginName()) + " ASC ");
        } else {
            sql = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 100, 0);
        }
        sql = provider.transSqlDialect(sql, datasourceRequest.getDsList());
        // replace placeholder
        sql = provider.replaceTablePlaceHolder(sql, originSql);

        logger.info("calcite data preview sql: " + sql);
        datasourceRequest.setQuery(sql);
        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        // 重新构造data
        List<TableField> fList = (List<TableField>) data.get("fields");
        List<DatasetTableFieldDTO> fields = transFields(fList, false);
        Map<String, Object> previewData = buildPreviewData(data, fields, new HashMap<>());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", previewData);
        map.put("sql", Base64.getEncoder().encodeToString(sql.getBytes()));
        return map;
    }

    public Map<String, Object> buildPreviewData(Map<String, Object> data, List<DatasetTableFieldDTO> fields, Map<String, ColumnPermissionItem> desensitizationList) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String[]> dataList = (List<String[]>) data.get("data");
        List<LinkedHashMap<String, Object>> dataObjectList = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(dataList)) {
            for (int i = 0; i < dataList.size(); i++) {
                String[] row = dataList.get(i);
                LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
                if (row.length > 0) {
                    for (int j = 0; j < fields.size(); j++) {
                        if (desensitizationList.keySet().contains(fields.get(j).getDataeaseName())) {
                            obj.put(fields.get(j).getDataeaseName(), ChartDataBuild.desensitizationValue(desensitizationList.get(fields.get(j).getDataeaseName()), String.valueOf(row[j])));
                        } else {
                            obj.put(ObjectUtils.isNotEmpty(fields.get(j).getDataeaseName()) ?
                                    fields.get(j).getDataeaseName() : fields.get(j).getOriginName(), row[j]);
                        }
                    }
                }
                dataObjectList.add(obj);
            }
        }

        map.put("fields", fields);
        map.put("data", dataObjectList);
        return map;
    }

    public void buildFieldName(Map<String, Object> sqlMap, List<DatasetTableFieldDTO> fields) {
        // 获取内层union sql和字段
        List<DatasetTableFieldDTO> unionFields = (List<DatasetTableFieldDTO>) sqlMap.get("field");
        for (DatasetTableFieldDTO datasetTableFieldDTO : fields) {
            DatasetTableFieldDTO dto = datasetTableFieldManage.selectById(datasetTableFieldDTO.getId());
            if (ObjectUtils.isEmpty(dto)) {
                if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                    for (DatasetTableFieldDTO fieldDTO : unionFields) {
                        if (Objects.equals(datasetTableFieldDTO.getDatasetTableId(), fieldDTO.getDatasetTableId())
                                && Objects.equals(datasetTableFieldDTO.getOriginName(), fieldDTO.getOriginName())) {
                            datasetTableFieldDTO.setDataeaseName(fieldDTO.getDataeaseName());
                            datasetTableFieldDTO.setFieldShortName(fieldDTO.getFieldShortName());
                        }
                    }
                }
                if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_CALC)) {
                    String dataeaseName = TableUtils.fieldNameShort(datasetTableFieldDTO.getId() + "_" + datasetTableFieldDTO.getOriginName());
                    datasetTableFieldDTO.setDataeaseName(dataeaseName);
                    datasetTableFieldDTO.setFieldShortName(dataeaseName);
                    datasetTableFieldDTO.setDeExtractType(datasetTableFieldDTO.getDeType());
                }
            } else {
                datasetTableFieldDTO.setDataeaseName(dto.getDataeaseName());
                datasetTableFieldDTO.setFieldShortName(dto.getFieldShortName());
            }
        }
    }

    /**
     * 解析预览请求中的 sortFields，使每个排序字段使用与内层 union 输出一致的 dataeaseName，
     * 避免 ORDER BY 使用前端传入的 dataeaseName 导致 "Unknown column 't_a_0.f_xxx' in 'order clause'"。
     */
    private List<DeSortField> resolveSortFieldsForPreview(List<DeSortField> sortFields,
                                                          List<DatasetTableFieldDTO> fields) {
        if (ObjectUtils.isEmpty(sortFields)) {
            return sortFields;
        }
        if (ObjectUtils.isEmpty(fields)) {
            return new ArrayList<>();
        }
        List<DeSortField> resolved = new ArrayList<>();
        for (DeSortField sf : sortFields) {
            DatasetTableFieldDTO match = null;
            for (DatasetTableFieldDTO f : fields) {
                boolean sameId = Objects.equals(sf.getId(), f.getId());
                boolean sameOrigin = Objects.equals(sf.getOriginName(), f.getOriginName())
                        && Objects.equals(sf.getDatasetTableId(), f.getDatasetTableId());
                boolean sameDataeaseName = StringUtils.isNotEmpty(sf.getDataeaseName())
                        && Objects.equals(sf.getDataeaseName(), f.getDataeaseName());
                if (sameId || sameOrigin || sameDataeaseName) {
                    match = f;
                    break;
                }
            }
            if (match != null && StringUtils.isNotEmpty(match.getDataeaseName())) {
                DeSortField resolvedField = new DeSortField();
                BeanUtils.copyBean(resolvedField, match);
                resolvedField.setOrderDirection(sf.getOrderDirection());
                resolved.add(resolvedField);
            }
        }
        return resolved;
    }

    public List<String> getFieldEnum(MultFieldValuesRequest multFieldValuesRequest) throws Exception {
        // 根据前端传的查询组件field ids，获取所有字段枚举值并去重合并
        List<List<String>> list = new ArrayList<>();
        logger.info("getFieldEnum called with fieldIds: " + multFieldValuesRequest.getFieldIds());
        for (String idStr : multFieldValuesRequest.getFieldIds()) {
            Long id = Long.parseLong(idStr);
            logger.info("Processing field id: " + id + " (parsed from: " + idStr + ")");
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(id);
            if (field == null) {
                logger.error("Field not found for id: " + id);
                DEException.throwException(Translator.get("i18n_no_field"));
            }
            List<DatasetTableFieldDTO> allFields = new ArrayList<>();
            // 根据图表计算字段，获取数据集
            Long datasetGroupId = field.getDatasetGroupId();
            if (field.getChartId() != null) {
                allFields.addAll(datasetTableFieldManage.getChartCalcFields(field.getChartId()));
            }
            DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.get(datasetGroupId, null);

            Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
            String sql = (String) sqlMap.get("sql");

            allFields.addAll(datasetGroupInfoDTO.getAllFields());

            Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
            boolean crossDs = Utils.isCrossDs(dsMap);
            if (!crossDs) {
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }

            // build query sql
            SQLMeta sqlMeta = new SQLMeta();
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);

            // 获取allFields
            List<DatasetTableFieldDTO> fields = Collections.singletonList(field);
            Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
            fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
            if (ObjectUtils.isEmpty(fields)) {
                DEException.throwException(Translator.get("i18n_no_column_permission"));
            }
            buildFieldName(sqlMap, fields);

            List<String> dsList = new ArrayList<>();
            for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
                dsList.add(next.getValue().getType());
            }
            boolean needOrder = Utils.isNeedOrder(dsList);

            List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
            TokenUserBO user = AuthUtils.getUser();
            if (user != null) {
                rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
            }

            Provider provider;
            if (crossDs) {
                provider = ProviderFactory.getDefaultProvider();
            } else {
                provider = ProviderFactory.getProvider(dsList.getFirst());
            }

            Field2SQLObj.field2sqlObj(sqlMeta, fields, allFields, crossDs, dsMap);
            WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap);
            Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), allFields, crossDs, dsMap);
            String querySQL;
            if (multFieldValuesRequest.getResultMode() == 0) {
                querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, true, 0, 1000);
            } else {
                querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, true);
            }
            querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
            logger.info("calcite data enum sql: " + querySQL);

            // 通过数据源请求数据
            // 调用数据源的calcite获得data
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setQuery(querySQL);
            datasourceRequest.setDsList(dsMap);

            Map<String, Object> data = provider.fetchResultField(datasourceRequest);
            List<String[]> dataList = (List<String[]>) data.get("data");
            dataList = dataList.stream().filter(row -> {
                boolean hasEmpty = false;
                for (String s : row) {
                    if (StringUtils.isBlank(s)) {
                        hasEmpty = true;
                        break;
                    }
                }
                return !hasEmpty;
            }).toList();
            List<String> previewData = new ArrayList<>();
            if (ObjectUtils.isNotEmpty(dataList)) {
                List<String> tmpData = dataList.stream().map(ele -> (ObjectUtils.isNotEmpty(ele) && ele.length > 0) ? ele[0] : null).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(tmpData)) {
                    if (desensitizationList.keySet().contains(field.getDataeaseName())) {
                        for (int i = 0; i < tmpData.size(); i++) {
                            previewData.add(ChartDataBuild.desensitizationValue(desensitizationList.get(field.getDataeaseName()), tmpData.get(i)));
                        }
                    } else {
                        previewData = tmpData;
                    }
                }
                list.add(previewData);
            }
        }

        // 重新构造data
        Set<String> result = new LinkedHashSet<>();
        for (List<String> l : list) {
            result.addAll(l);
        }
        return result.stream().toList();
    }

    public List<Map<String, Object>> getFieldEnumObj(EnumValueRequest request) throws Exception {
        List<Long> ids = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(request.getQueryId())) {
            ids.add(request.getQueryId());
        }
        if (ObjectUtils.isNotEmpty(request.getDisplayId())) {
            ids.add(request.getDisplayId());
        }

        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        if (ids.size() == 2 && Objects.equals(ids.get(0), ids.get(1))) {
            ids.remove(1);
        }

        SQLMeta sqlMeta = new SQLMeta();
        DatasetGroupInfoDTO datasetGroupInfoDTO = null;
        List<DatasetTableFieldDTO> fields = new ArrayList<>();
        Map<String, Object> sqlMap = null;
        boolean crossDs = false;
        Map<Long, DatasourceSchemaDTO> dsMap = null;

        if (ObjectUtils.isNotEmpty(request.getSortId())) {
            // 如果排序字段和查询字段显示字段不一致，则加入到查询列表中
            if (!request.getSortId().equals(request.getQueryId()) && !request.getSortId().equals(request.getDisplayId())) {
                ids.add(request.getSortId());
            }
        }

        List<DatasetTableFieldDTO> allFields = new ArrayList<>();

        for (Long id : ids) {
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(id);
            if (field == null) {
                DEException.throwException(Translator.get("i18n_no_field"));
            }

            // 根据图表计算字段，获取数据集
            Long datasetGroupId = field.getDatasetGroupId();
            if (field.getChartId() != null) {
                allFields.addAll(datasetTableFieldManage.getChartCalcFields(field.getChartId()));
            }
            datasetGroupInfoDTO = datasetGroupManage.get(datasetGroupId, null);

            sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
            String sql = (String) sqlMap.get("sql");

            allFields.addAll(datasetGroupInfoDTO.getAllFields());

            dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
            crossDs = Utils.isCrossDs(dsMap);
            if (!crossDs) {
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }

            // build query sql
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
            fields.add(field);
        }

        // 获取allFields
        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
        if (ObjectUtils.isEmpty(fields)) {
            DEException.throwException(Translator.get("i18n_no_column_permission"));
        }
        buildFieldName(sqlMap, fields);

        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);

        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
        TokenUserBO user = AuthUtils.getUser();
        if (user != null) {
            rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
        }

        //组件过滤条件
        List<ChartExtFilterDTO> extFilterList = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(request.getFilter())) {
            for (ChartExtFilterDTO filterDTO : request.getFilter()) {
                // 解析多个fieldId,fieldId是一个逗号分隔的字符串
                String fieldId = filterDTO.getFieldId();
                if (filterDTO.getIsTree() == null) {
                    filterDTO.setIsTree(false);
                }

                boolean hasParameters = false;
                List<SqlVariableDetails> sqlVariables = datasetGroupManage.getSqlParams(Arrays.asList(datasetGroupInfoDTO.getId()));
                if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(sqlVariables)) {
                    for (SqlVariableDetails parameter : Optional.ofNullable(filterDTO.getParameters()).orElse(new ArrayList<>())) {
                        String parameterId = StringUtils.endsWith(parameter.getId(), START_END_SEPARATOR) ? parameter.getId().split(START_END_SEPARATOR)[0] : parameter.getId();
                        if (sqlVariables.stream().map(SqlVariableDetails::getId).collect(Collectors.toList()).contains(parameterId)) {
                            hasParameters = true;
                        }
                    }
                }

                if (hasParameters) {
                    continue;
                }

                if (StringUtils.isNotEmpty(fieldId)) {
                    List<Long> fieldIds = Arrays.stream(fieldId.split(",")).map(Long::valueOf).collect(Collectors.toList());

                    if (filterDTO.getIsTree()) {
                        ChartExtFilterDTO filterRequest = new ChartExtFilterDTO();
                        BeanUtils.copyBean(filterRequest, filterDTO);
                        filterRequest.setDatasetTableFieldList(new ArrayList<>());
                        for (Long fId : fieldIds) {
                            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(fId);
                            if (datasetTableField == null) {
                                continue;
                            }
                            if (Objects.equals(datasetTableField.getDatasetGroupId(), datasetGroupInfoDTO.getId())) {
                                filterRequest.getDatasetTableFieldList().add(datasetTableField);
                            }
                        }
                        if (ObjectUtils.isNotEmpty(filterRequest.getDatasetTableFieldList())) {
                            extFilterList.add(filterRequest);
                        }
                    } else {
                        for (Long fId : fieldIds) {
                            ChartExtFilterDTO filterRequest = new ChartExtFilterDTO();
                            BeanUtils.copyBean(filterRequest, filterDTO);
                            filterRequest.setFieldId(fId + "");

                            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(fId);
                            if (datasetTableField == null) {
                                continue;
                            }
                            filterRequest.setDatasetTableField(datasetTableField);
                            if (Objects.equals(datasetTableField.getDatasetGroupId(), datasetGroupInfoDTO.getId())) {
                                extFilterList.add(filterRequest);
                            }
                        }
                    }
                }
            }
        }

        // 搜索备选项
        if (StringUtils.isNotEmpty(request.getSearchText())) {
            ChartExtFilterDTO dto = new ChartExtFilterDTO();
            DatasetTableFieldDTO field = null;
            if (ids.size() == 1) {
                field = datasetTableFieldManage.selectById(ids.get(0));
            } else {
                field = datasetTableFieldManage.selectById(ids.get(1));
            }
            dto.setDatasetTableField(field);
            dto.setFieldId(field.getId() + "");
            dto.setIsTree(false);
            dto.setOperator("like");
            dto.setValue(List.of(request.getSearchText()));
            extFilterList.add(dto);
        }

        // 排序
        boolean sortDistinct = true;
        if (ObjectUtils.isNotEmpty(request.getSortId())) {
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(request.getSortId());
            if (field == null) {
                DEException.throwException(Translator.get("i18n_no_field"));
            }
            DeSortField deSortField = new DeSortField();
            BeanUtils.copyBean(deSortField, field);
            deSortField.setOrderDirection(request.getSort());
            datasetGroupInfoDTO.setSortFields(Collections.singletonList(deSortField));
            sortDistinct = false;
        }

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsList.getFirst());
        }

        Field2SQLObj.field2sqlObj(sqlMeta, fields, allFields, crossDs, dsMap);
        ExtWhere2Str.extWhere2sqlOjb(sqlMeta, extFilterList, allFields, crossDs, dsMap);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap);
        Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), allFields, crossDs, dsMap);
        String querySQL;
        if (request.getResultMode() == 0) {
            querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, sortDistinct && ids.size() == 1, 0, 1000);
        } else {
            querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, sortDistinct && ids.size() == 1);
        }
        querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.info("calcite data enum sql: " + querySQL);

        // 通过数据源请求数据
        // 调用数据源的calcite获得data
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);

        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        List<String[]> dataList = (List<String[]>) data.get("data");
        dataList = dataList.stream().filter(row -> {
            boolean hasEmpty = false;
            for (String s : row) {
                if (StringUtils.isBlank(s)) {
                    hasEmpty = true;
                    break;
                }
            }
            return !hasEmpty;
        }).toList();
        Map<String, String[]> distinctData = new LinkedHashMap<>();
        for (String[] arr : dataList) {
            String key = Arrays.toString(arr);
            if (!distinctData.containsKey(key)) {
                distinctData.put(key, arr);
            }
        }

        List<String[]> distinctDataList = new ArrayList<>();
        for (Map.Entry<String, String[]> ele : distinctData.entrySet()) {
            distinctDataList.add(ele.getValue());
        }

        List<Map<String, Object>> previewData = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(distinctDataList)) {
            for (String[] ele : distinctDataList) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < fields.size(); i++) {
                    String val = ele[i];
                    DatasetTableFieldDTO field = fields.get(i);
                    if (desensitizationList.containsKey(field.getDataeaseName())) {
                        String str = ChartDataBuild.desensitizationValue(desensitizationList.get(field.getDataeaseName()), val);
                        map.put(field.getId() + "", str);
                    } else {
                        map.put(field.getId() + "", val);
                    }
                }
                previewData.add(map);
            }
        }
        return previewData;
    }

    public List<BaseTreeNodeDTO> getFieldValueTree(MultFieldValuesRequest multFieldValuesRequest) throws Exception {
        List<String> idStrs = multFieldValuesRequest.getFieldIds();
        if (idStrs.isEmpty()) {
            DEException.throwException("no field selected.");
        }
        // 根据前端传的查询组件field ids，获取所有字段枚举值并去重合并
        List<List<String>> list = new ArrayList<>();
        List<DatasetTableFieldDTO> fields = new ArrayList<>();

        // 根据图表计算字段，获取数据集
        List<DatasetTableFieldDTO> allFields = new ArrayList<>();
        Long id = Long.parseLong(idStrs.getFirst());
        DatasetTableFieldDTO field = datasetTableFieldManage.selectById(id);
        Long datasetGroupId = field.getDatasetGroupId();
        if (field.getChartId() != null) {
            allFields.addAll(datasetTableFieldManage.getChartCalcFields(field.getChartId()));
        }
        DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.get(datasetGroupId, null);

        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
        String sql = (String) sqlMap.get("sql");

        allFields.addAll(datasetGroupInfoDTO.getAllFields());

        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        boolean crossDs = Utils.isCrossDs(dsMap);
        if (!crossDs) {
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }

        // build query sql
        SQLMeta sqlMeta = new SQLMeta();
        Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);

        for (Long id : ids) {
            DatasetTableFieldDTO f = datasetTableFieldManage.selectById(id);
            if (f == null) {
                DEException.throwException(Translator.get("i18n_no_field"));
            }
            // 获取allFields
            fields.add(f);
        }

        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
        if (ObjectUtils.isEmpty(fields)) {
            DEException.throwException(Translator.get("i18n_no_column_permission"));
        }
        buildFieldName(sqlMap, fields);

        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);

        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
        TokenUserBO user = AuthUtils.getUser();
        if (user != null) {
            rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
        }

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsList.getFirst());
        }

        Field2SQLObj.field2sqlObj(sqlMeta, fields, allFields, crossDs, dsMap);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap);
        Order2SQLObj.getOrders(sqlMeta, datasetGroupInfoDTO.getSortFields(), allFields, crossDs, dsMap);
        String querySQL;
        if (multFieldValuesRequest.getResultMode() == 0) {
            querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, 0, 1000);
        } else {
            querySQL = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, false);
        }
        querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.info("filter tree sql: " + querySQL);

        // 通过数据源请求数据
        // 调用数据源的calcite获得data
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);

        Map<String, Object> data = provider.fetchResultField(datasourceRequest);
        List<String[]> rows = (List<String[]>) data.get("data");

        // 重新构造data
        Set<String> pkSet = new HashSet<>();
        rows = rows.stream().filter(row -> {
            boolean hasEmpty = false;
            for (String s : row) {
                if (StringUtils.isBlank(s)) {
                    hasEmpty = true;
                    break;
                }
            }
            return !hasEmpty;
        }).toList();
        List<BaseTreeNodeDTO> treeNodes = rows.stream().map(row -> buildTreeNode(row, pkSet)).flatMap(Collection::stream).collect(Collectors.toList());
        List<BaseTreeNodeDTO> tree = DatasetUtils.mergeDuplicateTree(treeNodes, "root");
        return tree;
    }

    private List<BaseTreeNodeDTO> buildTreeNode(String[] row, Set<String> pkSet) {
        List<BaseTreeNodeDTO> nodes = new ArrayList<>();
        List<String> parentPkList = new ArrayList<>();
        for (int i = 0; i < row.length; i++) {
            String text = row[i];

            parentPkList.add(text);
            String val = String.join(TreeUtils.SEPARATOR, parentPkList);
            String parentVal = i == 0 ? TreeUtils.DEFAULT_ROOT : row[i - 1];
            String pk = String.join(TreeUtils.SEPARATOR, parentPkList);
            if (pkSet.contains(pk)) continue;
            pkSet.add(pk);
            BaseTreeNodeDTO node = new BaseTreeNodeDTO(val, parentVal, StringUtils.isNotBlank(text) ? text.trim() : text, pk + TreeUtils.SEPARATOR + i);
            nodes.add(node);
        }
        return nodes;

    }
}
