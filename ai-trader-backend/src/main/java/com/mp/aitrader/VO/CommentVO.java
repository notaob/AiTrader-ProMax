package com.mp.aitrader.VO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommentVO {
    private Long id;
    private String userName;
    private String userAvatar;
    private String content;
    private String time;
}
