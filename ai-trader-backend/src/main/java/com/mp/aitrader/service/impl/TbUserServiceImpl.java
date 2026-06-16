package com.mp.aitrader.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mp.aitrader.Constant.JwtClaimsConstant;
import com.mp.aitrader.Constant.MessageConstant;
import com.mp.aitrader.DTO.LoginDTO;
import com.mp.aitrader.DTO.PasswordChangeDTO;
import com.mp.aitrader.DTO.UserDTO;
import com.mp.aitrader.Utils.JwtUtil;
import com.mp.aitrader.Utils.RegexUtils;
import com.mp.aitrader.VO.LoginVO;
import com.mp.aitrader.VO.Result;
import com.mp.aitrader.domain.TbUser;
import com.mp.aitrader.mapper.TbUserMapper;
import com.mp.aitrader.service.EmailService;
import com.mp.aitrader.service.TbUserService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import com.mp.aitrader.properties.JwtProperties;
import com.mp.aitrader.context.BaseContext;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static com.mp.aitrader.Constant.RedisConstants.*;


/**
* @author mkm
* @description 针对表【tb_user】的数据库操作Service实现
* @createDate 2025-11-10 20:47:32
*/
@Service
@Slf4j
public class TbUserServiceImpl extends ServiceImpl<TbUserMapper, TbUser> implements TbUserService {

    @Autowired
    private TbUserMapper userMapper;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private EmailService emailService;

    @Override
    public Result<String> sendCode(String email) {
        // 1.校验邮箱
        if (RegexUtils.isEmailInvalid(email)){
            return Result.error("邮箱格式错误");
        }
        // 2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.保存验证码到Redis
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + email, code, CACHE_NULL_TTL, TimeUnit.MINUTES);
        // 4.通过 Resend 发送验证码邮件
        emailService.sendVerificationCode(email, code);
        return Result.success("验证码已发送到邮箱");
    }

    @Override
    public Result<LoginVO> codeLogin(LoginDTO loginDTO) {
        // 1.校验邮箱
        String email = loginDTO.getEmail();
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.error("邮箱格式错误！");
        }
        // 2.校验验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        String code = loginDTO.getCode();
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.error("验证码格式错误！");
        }
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.error("验证码错误");
        }
        // 3.根据邮箱查询用户
        TbUser user = userMapper.getByEmail(email);

        // 4.判断用户是否存在
        if (user == null) {
            // 不存在，创建新用户
            user = createUserWithEmail(email);
        }

        // 5.生成jwt令牌
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );
        log.info("生成jwt令牌：{}", token);

        // 6.将User对象存入Redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        LoginVO loginVO = LoginVO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickName(user.getNickName())
                .icon(user.getIcon())
                .token(token)
                .build();

        return Result.success(loginVO);
    }

    private TbUser createUserWithEmail(String email) {
        TbUser user = new TbUser();
        user.setEmail(email);
        user.setNickName("user_" + RandomUtil.randomString(10));
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());

        userMapper.insert(user);

        return user;
    }

    @Override
    public Result<LoginVO> passwordLogin(LoginDTO loginDTO) {

        String email = loginDTO.getEmail();

        String password = loginDTO.getPassword();

        //根据邮箱查询用户
        TbUser user = userMapper.getByEmail(email);

        //判断用户是否存在
        if (user == null){
            throw new IllegalArgumentException(MessageConstant.PASSWORD_ERROR);
        }

        password = DigestUtils.md5DigestAsHex(password.getBytes());
        //判断密码是否正确
        if (!user.getPassword().equals(password)){
            throw new IllegalArgumentException(MessageConstant.ACCOUNT_LOCKED);
        }

        //登录成功后，生成jwt令牌
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );
        log.info("生成jwt令牌：{}", token);

        LoginVO loginVO = LoginVO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickName(user.getNickName())
                .icon(user.getIcon())
                .token(token)
                .build();

        return Result.success(loginVO);
    }

    @Override
    public Result<String> register(LoginDTO loginDTO) {
        // 1.校验邮箱
        String email = loginDTO.getEmail();
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.error("邮箱格式错误！");
        }
        // 2.校验验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        String code = loginDTO.getCode();
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.error("验证码格式错误！");
        }
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.error("验证码错误");
        }

        // 3.判断用户是否存在
        if (userMapper.getByEmail(email) != null) {
            return Result.error("用户已存在");
        }
        // 4.不存在，创建用户并保存
        TbUser user = new TbUser();
        user.setEmail(email);
        user.setNickName(loginDTO.getNickName());
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        user.setPassword(DigestUtils.md5DigestAsHex(loginDTO.getPassword().getBytes()));
        userMapper.insert(user);
        return Result.success("注册成功");
    }

    @Override
    public Result<String> resetPassword(LoginDTO loginDTO) {
        // 1.校验邮箱
        String email = loginDTO.getEmail();
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.error("邮箱格式错误！");
        }
        // 2.校验验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        String code = loginDTO.getCode();
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.error("验证码格式错误！");
        }
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.error("验证码错误");
        }

        // 3.查询用户是否存在
        TbUser user = userMapper.getByEmail(email);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 4.更新密码
        user.setPassword(DigestUtils.md5DigestAsHex(loginDTO.getPassword().getBytes()));
        user.setUpdateTime(new Date());
        userMapper.updateById(user);

        return Result.success("密码重置成功");
    }

    @Override
    public Result<String> updateUserInfo(LoginDTO loginDTO) {
        Long userId = BaseContext.getCurrentId();
        TbUser user = new TbUser();
        user.setId(userId);
        if (loginDTO.getNickName() != null) {
            user.setNickName(loginDTO.getNickName());
        }
        user.setUpdateTime(new Date());
        userMapper.updateById(user);
        return Result.success("用户信息更新成功");
    }

    @Override
    public Result<String> changePassword(PasswordChangeDTO passwordChangeDTO) {
        Long userId = BaseContext.getCurrentId();
        TbUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 校验旧密码
        String oldPassword = DigestUtils.md5DigestAsHex(passwordChangeDTO.getOldPassword().getBytes());
        if (!user.getPassword().equals(oldPassword)) {
            return Result.error("旧密码错误");
        }

        // 更新新密码
        String newPassword = DigestUtils.md5DigestAsHex(passwordChangeDTO.getNewPassword().getBytes());
        user.setPassword(newPassword);
        user.setUpdateTime(new Date());
        userMapper.updateById(user);

        return Result.success("密码修改成功");
    }

    @Override
    public Result<String> uploadAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        // 校验文件类型 (仅允许图片)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("请上传图片文件");
        }

        // 获取原始文件名
        String originalFilename = file.getOriginalFilename();
        // 获取文件后缀
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // 生成新文件名
        String fileName = UUID.randomUUID().toString() + suffix;

        // 文件保存路径
        String filePath = System.getProperty("user.dir") + "/upload/";
        File dir = new File(filePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            // 保存文件
            file.transferTo(new File(filePath + fileName));

            // 更新用户头像
            Long userId = BaseContext.getCurrentId();
            TbUser user = new TbUser();
            user.setId(userId);
            String fileUrl = "/upload/" + fileName;
            user.setIcon(fileUrl);
            user.setUpdateTime(new Date());
            userMapper.updateById(user);

            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败");
        }
    }

    @Override
    public Result<LoginVO> getCurrentUser() {
        // 1. 获取当前登录用户ID
        Long userId = BaseContext.getCurrentId();

        // 2. 查询用户信息
        TbUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 3. 封装返回对象
        LoginVO loginVO = LoginVO.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .nickName(user.getNickName())
                .icon(user.getIcon())
                .vipLevel(user.getVipLevel())
                .aiChance(user.getAiChance())
                .point(user.getPoint())
                .build();

        return Result.success(loginVO);
    }

    @Override
    public Result<String> logout(String token) {
            try {
                // 移除Bearer前缀（如果存在）
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }

                // 解析JWT获取过期时间
                Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
                Date expiration = claims.getExpiration();
                long expireInSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;


                // 确保过期时间是正数
                if (expireInSeconds <= 0) {
                    expireInSeconds = 3600; // 默认1小时
                }

                // 加入黑名单
                redisTemplate.opsForValue().set(
                        "jwt:blacklist:" + token,
                        "invalid",
                        expireInSeconds,
                        TimeUnit.SECONDS
                );
                return Result.success("登出成功");
            }catch (Exception e){
                log.error("登出失败", e);
                return Result.error("登出失败");
            }
        }
}
