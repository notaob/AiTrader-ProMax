package com.mp.aitrader.controller;

import com.mp.aitrader.DTO.LoginDTO;
import com.mp.aitrader.VO.LoginVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.service.TbUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private TbUserService userService;

    @PostMapping("code")
    public Result<String> sendCode(@RequestParam("phone") String phone) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone);
    }
    /**
     * 密码登录
     * @param loginDTO
     * @return
     */
    @PostMapping("/login/password")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {

        log.info("员工登录：{}", loginDTO);

        return userService.passwordLogin(loginDTO);

    }

    /**
     * 短信登录
     * @param loginDTO
     * @return
     */
    @PostMapping("/login/sms")
    public Result<LoginVO> smsLogin(@RequestBody LoginDTO loginDTO) {
        return userService.smsLogin(loginDTO);
    }
    /**
     * 注册
     * @param loginDTO
     * @return
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody LoginDTO loginDTO) {
        return userService.register(loginDTO);
    }

    /**
     * 重置密码
     * @param loginDTO
     * @return
     */
    @PostMapping("/resetPassword")
    public Result<String> resetPassword(@RequestBody LoginDTO loginDTO) {
        return userService.resetPassword(loginDTO);
    }

    /**
     * 更新用户信息
     * @param loginDTO
     * @return
     */
    @PostMapping("/update")
    public Result<String> updateUserInfo(@RequestBody LoginDTO loginDTO) {
        return userService.updateUserInfo(loginDTO);
    }

    /**
     * 上传头像
     * @param file
     * @return
     */
    @PostMapping("/upload")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return userService.uploadAvatar(file);
    }

    /**
     * 获取当前用户信息
     * @return
     */
    @GetMapping("/me")
    public Result<LoginVO> getCurrentUser() {
        return userService.getCurrentUser();
    }

    /**
     * 登出
     * @return
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        return userService.logout(token);
    }

    /**
     * 修改密码
     * @param passwordChangeDTO
     * @return
     */
    @PostMapping("/password/change")
    public Result<String> changePassword(@RequestBody com.mp.aitrader.DTO.PasswordChangeDTO passwordChangeDTO) {
        return userService.changePassword(passwordChangeDTO);
    }
}
