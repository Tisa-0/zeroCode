package io.dataease.api.dataset.dto;

import io.dataease.extensions.datasource.dto.DatasetTableFieldDTO;
import lombok.Data;

@Data
public class DeSortField extends DatasetTableFieldDTO {

    private String orderDirection;
}
