package com.sht.zdaicode.ai.model.scheam;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ProjectScheam implements Serializable {
    private String projectName;
    private String description;
    // 所有的实体/数据表集合
    private List<EntityScheam> entities;

    private static final long serialVersionUID = 1L;
}
