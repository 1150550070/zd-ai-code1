package com.sht.zdaicode.ai.model.scheam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectScheam implements Serializable {
    private String projectName;
    private String description;
    // 所有的实体/数据表集合
    private List<EntityScheam> entities;

    private static final long serialVersionUID = 1L;
}
