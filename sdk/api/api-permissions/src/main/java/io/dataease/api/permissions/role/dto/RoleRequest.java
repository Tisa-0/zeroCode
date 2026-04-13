package io.dataease.api.permissions.role.dto;

import io.dataease.model.KeywordRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "角色过滤器")
@EqualsAndHashCode(callSuper = true)
@Data
public class RoleRequest extends KeywordRequest {
    private static final long serialVersionUID = 7354856549096378406L;
    @Schema(description = "用户ID")
    private Long uid;
}
