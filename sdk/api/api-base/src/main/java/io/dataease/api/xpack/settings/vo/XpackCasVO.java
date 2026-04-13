package io.dataease.api.xpack.settings.vo;

import lombok.Data;
import java.io.Serializable;

@Data
public class XpackCasVO implements Serializable {
    private static final long serialVersionUID = 3748231475265743038L;

    private String idpUri;

    private String casCallbackDomain;
}
