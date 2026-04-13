# Backend Independent Extraction Roadmap

## Goal

Keep existing front-end and back-end features, but progressively remove hard dependencies on the DataEase framework contracts (`sdk/api`, `sdk/extensions`, `license-sdk`) in the backend implementation.

This document focuses on **what can be extracted first** and **how to do it without breaking existing behavior**.

## Current Coupling Snapshot

Source scope: `core/core-backend/src/main/java`

- `import io.dataease.api.*`: `112`
- `import io.dataease.extensions.*`: `146`
- `import io.dataease.license.*`: `17`

Hotspot distribution (files with these imports):

- `dataset`: `83`
- `datasource`: `64`
- `engine`: `54`
- `system`: `8`
- `commons`: `6`

Conclusion:

- The highest extraction value is in the `dataset + datasource + engine` chain.
- The safest first cut is `copilot` because the boundary is already HTTP I/O + JSON plan.

## Modules That Can Be Extracted First

Detailed operation-node migration matrix (join/union/transform/sample/sort/pivot/unpivot/group/selfloop/mirror/deduplicate):

- [operation-node-migration-plan.md](/E:/BI/dataEase/dataease/docs/operation-node-migration-plan.md)

### 1. Copilot Orchestration Module (High Value, Low Risk)

Why first:

- It is already an external call + response normalization workflow.
- It can expose a new API without touching existing dataset runtime immediately.

Core files:

- [CopilotServer.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/copilot/server/CopilotServer.java)
- [CopilotManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/copilot/manage/CopilotManage.java)

Independent endpoint added in this round:

- [IndependentCopilotServer.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/copilot/server/IndependentCopilotServer.java)
- Path: `POST /api/independent/copilot/chat`

Independent DTOs added:

- [CopilotPlanRequest.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/copilot/dto/CopilotPlanRequest.java)
- [CopilotPlanResponse.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/copilot/dto/CopilotPlanResponse.java)

Service boundary extracted:

- [CopilotPlanService.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/copilot/service/CopilotPlanService.java)

Notes:

- Old `CopilotApi + MsgDTO` path is still kept for compatibility.
- New path no longer depends on `io.dataease.api.copilot.*`.

### 2. Canvas Arrange Domain Module (High Value, Medium Risk)

Why next:

- This is the business core for “natural language -> canvas operations”.
- Current logic is spread across `dataset`, `datasource`, and `engine` packages.

Primary extraction targets:

- [DatasetDataManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetDataManage.java)
- [DatasetSQLManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetSQLManage.java)
- [DatasetGroupManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetGroupManage.java)
- [DatasourceServer.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/datasource/server/DatasourceServer.java)
- [CalciteProvider.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/datasource/provider/CalciteProvider.java)

Recommended extraction form:

- create an internal domain package such as `io.dataease.independent.arrange`
- define internal commands/events:
  - `ArrangeCommand`
  - `ArrangeNode`
  - `ArrangeLink`
  - `JoinConfig`
- keep current API payload conversion in adapter layer only

### 3. Plugin/Datasource Adapter Module (Medium Value, Medium Risk)

Current hard dependency examples:

- `io.dataease.extensions.datasource.*`
- `ProviderFactory` usage from `extensions`

Key coupling files:

- [DatasourceServer.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/datasource/server/DatasourceServer.java)
- [DatasetDataManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetDataManage.java)
- [SqlparserUtils.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/commons/utils/SqlparserUtils.java)

Extraction strategy:

- introduce local adapter interface (example: `DatasourcePluginGateway`)
- keep `extensions` implementation as one adapter implementation
- allow replacement with non-DataEase implementation later

### 4. Permission/Org Adapter Module (Medium Value, Medium Risk)

Current dependency source:

- `io.dataease.api.permissions.*`

Key files:

- [PermissionManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/PermissionManage.java)
- [UserFeignService.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/defeign/permissions/user/UserFeignService.java)
- [PermissionFeignService.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/defeign/permissions/auth/PermissionFeignService.java)

Extraction strategy:

- define local `PermissionGateway` and `UserGateway`
- move DTO mapping to adapter layer
- keep `defeign` implementation as legacy adapter

### 5. License Feature-Flag Adapter (Medium Value, Low Risk)

Current dependency source:

- `io.dataease.license.*`

Key files:

- [DataSourceManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/datasource/manage/DataSourceManage.java)
- [DatasetGroupManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetGroupManage.java)
- [XpackTaskStarter.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/listener/XpackTaskStarter.java)

Extraction strategy:

- add `FeatureFlagGateway` interface in backend
- `LicenseUtil` and `XpackInteract` only appear inside one implementation
- business services depend only on local gateway interface

## Incremental Refactor Plan

### Phase 1 (completed in this round)

- Extract copilot Ark-call orchestration service.
- Add independent endpoint and independent request/response contracts.
- Keep old endpoint behavior unchanged.

### Phase 2 (next)

- Introduce `independent.arrange` domain model.
- Add one independent endpoint for canvas plan apply/preview.
- Move join-node/left-join normalization logic into this local domain service.

### Phase 2 Progress (implemented in this round)

- Added independent arrange runtime services:
  - [ArrangeOperationService.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/independent/arrange/service/ArrangeOperationService.java)
  - [ArrangeFieldMeta.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/independent/arrange/model/ArrangeFieldMeta.java)
  - [ArrangeJoinSqlService.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/independent/arrange/service/ArrangeJoinSqlService.java)
- `DatasetDataManage` now delegates operation support checks and runtime execution (`union/sample/deduplicate/group`, mirror-aware source resolve) to `ArrangeOperationService`.
- `DatasetSQLManage` now delegates union/join type SQL keyword conversion to `ArrangeJoinSqlService`.
- Existing API behavior remains unchanged while executor logic is moved behind independent service boundaries.

### Phase 3

- Add datasource plugin adapter abstraction.
- Migrate `dataset` and `datasource` services to depend on local gateway interfaces.

### Phase 4

- Add permission and feature-flag gateways.
- Remove direct dependency from business services to `sdk/api` and `license` contracts.

## Acceptance Criteria Per Phase

- Existing front-end behavior keeps working.
- New independent endpoint can be switched on from front-end gradually.
- Adapter coverage increases while old DataEase contracts remain as fallback.
- No forced one-shot migration.
