package com.mp.aitrader.VO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromotionVO {
    private Long id;
    private String title;
    private String description;
    private String actionText;
    private String actionColor;
    private String type;
    private Integer requiredPoints;
}
