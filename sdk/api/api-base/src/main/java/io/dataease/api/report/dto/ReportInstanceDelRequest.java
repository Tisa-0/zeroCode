package io.dataease.api.report.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class ReportInstanceDelRequest implements Serializable {
    private static final long serialVersionUID = 3972312402268432597L;

    private Long instanceId;

    private Integer timeFlag;

    private Long taskId;
}
