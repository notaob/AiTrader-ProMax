package com.mp.aitrader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mp.aitrader.domain.TbUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;


/**
* @author mkm
* @description 针对表【tb_user】的数据库操作Mapper
* @createDate 2025-11-10 20:47:32
* @Entity generator.domain.TbUser
*/
@Mapper
public interface TbUserMapper extends BaseMapper<TbUser> {

    TbUser getByUsername(@Param("phone") String username);

    TbUser getByEmail(@Param("email") String email);

    /**
     * 原子扣减 AI 机会：仅当 ai_chance &gt; 0 时扣减，以影响行数判定成败（0 = 余额不足）。
     *
     * <p>替代原来的「select → 校验 → updateById」三步写法：并发下两个请求可同时读到 ai_chance=1
     * 并双双通过校验，导致超扣。本写法把判断与扣减压进一条 UPDATE，天然并发安全，无需加锁。
     */
    @Update("UPDATE tb_user SET ai_chance = ai_chance - 1, update_time = NOW() WHERE id = #{userId} AND ai_chance > 0")
    int deductAiChance(@Param("userId") Long userId);

}




