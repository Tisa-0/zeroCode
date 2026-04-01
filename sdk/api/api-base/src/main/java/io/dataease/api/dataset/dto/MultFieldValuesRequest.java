package io.dataease.api.dataset.dto;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MultFieldValuesRequest {
    // 使用 String 类型避免 JavaScript 大数字精度丢失问题
    List<String> fieldIds = new ArrayList<>();
    Long userId = null;

    private DeSortDTO sort;
    private Integer resultMode = 0;

}
