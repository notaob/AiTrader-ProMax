package com.mp.aitrader.VO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
public class LoginVO implements Serializable {

    private Long id;

    private String phone;

    private String nickName;

    private String icon;

    private Integer vipLevel;

    private Integer aiChance;

    private Integer point;

    private String token;


}
