# JDK 8 Downgrade Assessment

## Goal

Assess the work required to make the backend run on JDK 8 while minimizing impact on current functionality.

## Executive Summary

The current backend is not compatible with JDK 8.

This is not a small compiler-target adjustment. It is a full platform downgrade with three major blockers:

1. The project is built on Spring Boot 3.3, which requires Java 17+.
2. The codebase uses `jakarta.*` APIs introduced by the Spring Boot 3 / Spring 6 ecosystem.
3. The codebase already uses Java 9+ / 10+ / 16+ language and library features such as `List.of`, `Map.of`, `var`, `Stream.toList`, and pattern matching `instanceof`.

Because of that, a safe downgrade path means:

- Spring Boot 3.3 -> Spring Boot 2.7.x
- Spring Framework 6 -> Spring Framework 5.3.x
- Jakarta servlet/validation/annotation APIs -> javax equivalents
- Java source level 21 -> 8
- Refactor Java 9+ / 10+ / 16+ features across backend and shared SDK modules

## Current Version Baseline

Key build settings are defined in:

- [pom.xml](/E:/BI/dataEase/dataease/pom.xml#L14)
- [pom.xml](/E:/BI/dataEase/dataease/pom.xml#L23)
- [pom.xml](/E:/BI/dataEase/dataease/pom.xml#L27)
- [pom.xml](/E:/BI/dataEase/dataease/pom.xml#L28)

Original baseline on the main line:

- Spring Boot parent: `3.3.0`
- `java.version`: `21`
- `maven.compiler.source`: `21`
- `maven.compiler.target`: `21`

Experimental baseline already adjusted on the downgrade branch:

- Spring Boot parent: `2.6.13`
- `java.version`: `1.8`
- `maven.compiler.source`: `1.8`
- `maven.compiler.target`: `1.8`
- Spring Cloud: `2021.0.5`
- Spring Cloud Alibaba: `2021.0.5.0`

This baseline now allows Maven to move past the first BOM resolution stage and reach real compile-time blockers.

## Blocking Areas

### 1. Spring Boot / Spring Version Chain

Current backend depends on Spring Boot 3.3.0, which requires Java 17+.

This means JDK 8 compatibility cannot be achieved without downgrading the platform stack itself.

Required direction:

- `spring-boot-starter-parent` -> `2.7.x`
- Spring Framework 6 -> Spring Framework 5
- Revalidate all Spring-managed integrations:
  - web
  - websocket
  - validation
  - boot auto-configuration
  - actuator-like lifecycle hooks if any

### 2. Jakarta to javax Migration

The codebase already imports many `jakarta.*` classes, which are aligned with Spring Boot 3.

Current scan result on `sdk` + `core/core-backend`: `66` direct `jakarta.*` import statements.

Representative files:

- [ServletUtils.java](/E:/BI/dataEase/dataease/sdk/common/src/main/java/io/dataease/utils/ServletUtils.java#L4)
- [TokenFilter.java](/E:/BI/dataEase/dataease/sdk/common/src/main/java/io/dataease/auth/filter/TokenFilter.java#L6)
- [PwdLoginDTO.java](/E:/BI/dataEase/dataease/sdk/api/api-permissions/src/main/java/io/dataease/api/permissions/login/dto/PwdLoginDTO.java#L5)
- [LoginApi.java](/E:/BI/dataEase/dataease/sdk/api/api-permissions/src/main/java/io/dataease/api/permissions/login/api/LoginApi.java#L10)
- [CopilotManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/copilot/manage/CopilotManage.java#L12)

Main migration categories:

- `jakarta.servlet.*` -> `javax.servlet.*`
- `jakarta.servlet.http.*` -> `javax.servlet.http.*`
- `jakarta.annotation.*` -> `javax.annotation.*`
- `jakarta.validation.*` -> `javax.validation.*`

This will affect:

- filters
- interceptors
- controllers / API interfaces
- DTO validation annotations
- resource injection annotations
- servlet response helpers

### 3. Java Language and API Usage Above JDK 8

The codebase uses multiple post-Java-8 features that must be rewritten.

#### `List.of` / `Map.of`

Examples:

- [DatasetDataManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetDataManage.java#L79)
- [DatasetSQLManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetSQLManage.java#L65)
- [DatasourceServer.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/datasource/server/DatasourceServer.java#L754)
- [WhitelistUtils.java](/E:/BI/dataEase/dataease/sdk/common/src/main/java/io/dataease/utils/WhitelistUtils.java#L23)

JDK 8 replacement pattern:

- `List.of(...)` -> `Arrays.asList(...)` or manual list construction
- `Map.of(k, v)` -> `Collections.singletonMap(k, v)` or manual map construction

#### `var`

Examples:

- [DatasetGroupManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetGroupManage.java#L593)

JDK 8 replacement pattern:

- explicit declared types

#### `Stream.toList()`

Examples:

- [SysParameterManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/system/manage/SysParameterManage.java#L96)
- [DatasetTableFieldManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetTableFieldManage.java#L242)
- [TreeUtils.java](/E:/BI/dataEase/dataease/sdk/common/src/main/java/io/dataease/utils/TreeUtils.java#L30)

JDK 8 replacement pattern:

- `.collect(Collectors.toList())`

#### Pattern Matching `instanceof`

Examples:

- [DatasetDataManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetDataManage.java#L681)
- [DatasetDataManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetDataManage.java#L960)
- [DatasetDataManage.java](/E:/BI/dataEase/dataease/core/core-backend/src/main/java/io/dataease/dataset/manage/DatasetDataManage.java#L1019)

JDK 8 replacement pattern:

- classic `instanceof` + cast

### 4. Shared SDK Impact

This is not only a `core-backend` problem.

Shared modules under `sdk` also need downgrade work:

- `sdk/common`
- `sdk/api`
- `sdk/distributed`
- `sdk/extensions`

Examples:

- [extensions/pom.xml](/E:/BI/dataEase/dataease/sdk/extensions/pom.xml#L21)
- [extensions-datasource/pom.xml](/E:/BI/dataEase/dataease/sdk/extensions/extensions-datasource/pom.xml#L40)
- [extensions-view/pom.xml](/E:/BI/dataEase/dataease/sdk/extensions/extensions-view/pom.xml#L22)

These modules currently also target Java 21.

### 5. External Binary Compatibility Blocker

The first compile run after the BOM downgrade no longer fails on Maven metadata. It now fails on a bytecode incompatibility in an external dependency:

- `io.dataease:dataease-license-sdk:2.9.0`

Observed compile failure:

- [ProviderFactory.java](/E:/BI/dataEase/dataease/sdk/extensions/extensions-datasource/src/main/java/io/dataease/extensions/datasource/factory/ProviderFactory.java#L3)
- referenced class file:
  - `E:\BI\dataEase\.m2\repository\io\dataease\dataease-license-sdk\2.9.0\dataease-license-sdk-2.9.0.jar`
- class version: `61.0` (Java 17)
- required by JDK 8 compiler/runtime: `52.0`

This is an important sequencing finding:

- before fixing `jakarta` imports in later modules, the downgrade path is already blocked by a Java 17-compiled binary artifact;
- this jar is not built from visible source in the current workspace, so it must be handled as an external dependency risk;
- if no JDK 8-compatible variant of `dataease-license-sdk` exists, then full downgrade of `sdk/extensions` and any module depending on that jar cannot complete on JDK 8.

Related references:

- [sdk/api/pom.xml](/E:/BI/dataEase/dataease/sdk/api/pom.xml#L29)
- [sdk/extensions/pom.xml](/E:/BI/dataEase/dataease/sdk/extensions/pom.xml#L32)
- local source already contains its own `DEException`, which confirms the current failure is caused by the jar shadowing a same-package class:
  - [DEException.java](/E:/BI/dataEase/dataease/sdk/common/src/main/java/io/dataease/exception/DEException.java#L1)

## Dependency Risks Beyond Compilation

Even after source compatibility changes, the following dependencies may still require verification or version downgrades for JDK 8:

- Spring Boot starter set
- Spring Cloud Alibaba / Spring Cloud BOM alignment
- Selenium
- MyBatis Plus integration version compatibility
- websocket stack
- mail stack
- Quartz starter

This needs a BOM-level review after the Spring Boot downgrade target is fixed.

## Recommended Downgrade Path

### Phase 1. Freeze Scope

Decide whether the goal is:

- compile on JDK 8 only
- compile and run standalone backend on JDK 8
- full product compatibility on JDK 8 including distributed / extensions

Recommendation:

- start with `core-backend + sdk/common + sdk/api`
- defer `sdk/distributed` and `sdk/extensions` unless required

### Phase 2. Platform Downgrade

Create a dedicated downgrade branch and first move the build platform:

- Spring Boot `3.3.0` -> `2.7.x`
- Java source/target `21` -> `8`
- align Spring Cloud / Alibaba versions to a Boot 2.7-compatible set

Status:

- partially completed on the downgrade branch
- Boot `2.6.13` baseline is already in place and sufficient for continued assessment
- `flyway-mysql` had to be explicitly version-managed again after the BOM downgrade

### Phase 3. Namespace Migration

Bulk replace:

- `jakarta.servlet` -> `javax.servlet`
- `jakarta.annotation` -> `javax.annotation`
- `jakarta.validation` -> `javax.validation`

### Phase 4. Java Syntax/API Refactor

Refactor all:

- `List.of`
- `Map.of`
- `Set.of`
- `var`
- `.toList()`
- pattern matching `instanceof`

### Phase 5. Module-by-Module Compile Repair

Recommended order:

1. handle external Java 17 jar compatibility (`dataease-license-sdk`)
2. `sdk/common`
3. `sdk/api`
4. `core/core-backend`
5. optional `sdk/distributed`
6. optional `sdk/extensions`

### Phase 6. Runtime Verification

Verify at least:

- startup
- login/token filter
- websocket init
- dataset tree/details
- dataset preview
- AI copilot endpoint
- datasource management
- scheduled task initialization

## Effort / Risk Estimate

Estimated risk: high

Why:

- platform downgrade, not just code cleanup
- shared SDK impact is broad
- Boot 3 -> Boot 2 rollback often causes transitive dependency conflicts
- `jakarta` migration touches core request lifecycle code

Estimated effort:

- assessment + BOM alignment: medium
- source refactor: medium to high
- runtime stabilization: high

## Practical Recommendation

If the business requirement is only environment compatibility, the safest practical target is:

- frontend: Node 18.20.8 + npm 10.8.2 + Vite 4.5.13
- backend: JDK 17 or JDK 21

If JDK 8 is truly mandatory, proceed only on the dedicated downgrade branch and treat it as a separate migration project.

## Suggested Next Step

If we continue on the downgrade branch, the next concrete action should be:

1. confirm whether `dataease-license-sdk` has a JDK 8-compatible artifact or source branch
2. if yes, replace or rebuild that artifact first
3. then continue compile repair from `sdk/common`
4. after that, batch-fix `jakarta.*` and Java 9+ syntax in `sdk/common` / `sdk/api` / `core-backend`
