package com.mp.aitrader.VO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MomentLikeVO {
    private Boolean isLiked;
    private Integer likes;
}
