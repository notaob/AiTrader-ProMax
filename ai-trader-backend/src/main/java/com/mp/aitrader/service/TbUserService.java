package com.mp.aitrader.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mp.aitrader.DTO.LoginDTO;
import com.mp.aitrader.DTO.PasswordChangeDTO;
import com.mp.aitrader.VO.LoginVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.domain.TbUser;
import org.springframework.web.multipart.MultipartFile;

/**
* @author mkm
* @description 针对表【tb_user】的数据库操作Service
* @createDate 2025-11-10 20:47:32
*/
public interface TbUserService extends IService<TbUser> {

    Result<String> sendCode(String email);

    Result<LoginVO> codeLogin(LoginDTO loginDTO);

    Result<LoginVO> passwordLogin(LoginDTO loginDTO);

    Result<String> register(LoginDTO loginDTO);

    Result<String> resetPassword(LoginDTO loginDTO);

    Result<String> updateUserInfo(LoginDTO loginDTO);

    Result<String> changePassword(PasswordChangeDTO passwordChangeDTO);

    Result<String> uploadAvatar(MultipartFile file);

    Result<LoginVO> getCurrentUser();

    Result<String> logout(String token);


}
