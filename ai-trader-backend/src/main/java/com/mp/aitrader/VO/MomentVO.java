package com.mp.aitrader.VO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MomentVO {
    private Long id;
    private String userName;
    private String userAvatar;
    private String time;
    private String content;
    private Integer likes;
    private Integer comments;
    private Boolean isLiked;
}
