package io.dataease.api.communicate.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class MessageFile implements Serializable {
    private static final long serialVersionUID = 7140452847688399889L;

    private String fileName;

    private byte[] body;

}
