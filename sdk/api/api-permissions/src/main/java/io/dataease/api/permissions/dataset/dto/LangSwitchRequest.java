package io.dataease.api.permissions.dataset.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class LangSwitchRequest implements Serializable {
    private static final long serialVersionUID = -6779697711311519431L;


    private String lang;
}
