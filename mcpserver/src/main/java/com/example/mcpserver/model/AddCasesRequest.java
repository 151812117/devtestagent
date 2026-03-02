package com.example.mcpserver.model;

import lombok.Data;
import java.util.List;

/**
 * 添加案例到批次请求
 */
@Data
public class AddCasesRequest {
    
    /**
     * 案例编号列表
     */
    private List<String> caseIds;
    
    /**
     * 批次编号
     */
    private String batchId;
}
