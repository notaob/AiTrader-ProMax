package com.mp.aitrader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mp.aitrader.domain.TbUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


/**
* @author mkm
* @description 针对表【tb_user】的数据库操作Mapper
* @createDate 2025-11-10 20:47:32
* @Entity generator.domain.TbUser
*/
@Mapper
public interface TbUserMapper extends BaseMapper<TbUser> {

    TbUser getByUsername(@Param("phone") String username);

}




