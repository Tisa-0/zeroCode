package io.dataease.api.license.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class LicenseRequest implements Serializable {
    private static final long serialVersionUID = 537166561964245306L;
    private String license;
}
