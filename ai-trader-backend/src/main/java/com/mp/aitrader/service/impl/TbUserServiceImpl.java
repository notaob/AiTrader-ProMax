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

    @Override
    public Result<String> sendCode(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //不符合返回错误消息
            return Result.error("手机号格式错误");
        }
        // 2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.保存验证码到Redis
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,CACHE_NULL_TTL, TimeUnit.MINUTES);
        // 4.发送验证码
        log.debug("发送短信验证码成功:{}",code);
        return Result.success("发送短信验证码成功");
    }

    @Override
    public Result<LoginVO> smsLogin(LoginDTO loginDTO) {
        // 1.校验手机号
        String phone = loginDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.error("手机号格式错误！");
        }
        // 3.校验验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginDTO.getCode();
        if (RegexUtils.isCodeInvalid(code)) {
            // 2.如果不符合，返回错误信息
            return Result.error("验证码格式错误！");
        }
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不一致，报错
            return Result.error("验证码错误");
        }
        // 4.根据手机号查询用户
        TbUser user = userMapper.getByUsername(phone);

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // 7.保存用户信息到 redis中

        // 7.1登录成功后，生成jwt令牌
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID,user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );
        log.info("生成jwt令牌：{}", token);

        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        LoginVO loginVO = LoginVO.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .nickName(user.getNickName())
                .icon(user.getIcon())
                .token(token)
                .build();

        // 8.返回token
        return Result.success(loginVO);
    }

    private TbUser createUserWithPhone(String phone) {
        TbUser user = new TbUser();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        user.setCreateTime(new Date()); // 设置创建时间
        user.setUpdateTime(new Date()); // 设置更新时间

        userMapper.insert(user);

        return user;
    }

    @Override
    public Result<LoginVO> passwordLogin(LoginDTO loginDTO) {

        String username = loginDTO.getPhone();

        String password = loginDTO.getPassword();

        //根据用户名查询数据库
        TbUser user = userMapper.getByUsername(username);

/*        // 使用MyBatis Plus的条件构造器替代自定义SQL
        TbUser user = this.getOne(new QueryWrapper<TbUser>().eq("phone", username));*/


        //判断用户是否存在
        if (user == null){
            //密码错误
            throw new IllegalArgumentException(MessageConstant.PASSWORD_ERROR);
        }

        password = DigestUtils.md5DigestAsHex(password.getBytes());
        //判断密码是否正确
        if (!user.getPassword().equals(password)){
            //账号被锁定
            throw new IllegalArgumentException(MessageConstant.ACCOUNT_LOCKED);
        }

        //登录成功后，生成jwt令牌
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID,user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );
        log.info("生成jwt令牌：{}", token);


        LoginVO loginVO = LoginVO.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .nickName(user.getNickName())
                .icon(user.getIcon())
                .token(token)
                .build();

        return Result.success(loginVO);
    }

    @Override
    public Result<String> register(LoginDTO loginDTO) {
        // 1.校验手机号
        String phone = loginDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.error("手机号格式错误！");
        }
        // 3.校验验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginDTO.getCode();
        if (RegexUtils.isCodeInvalid(code)) {
            // 2.如果不符合，返回错误信息
            return Result.error("验证码格式错误！");
        }
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不一致，报错
            return Result.error("验证码错误");
        }

        // 4.判断用户是否存在
        if (userMapper.getByUsername(phone)!= null) {
            return Result.error("用户已存在");
        }
        // 5.不存在，创建用户并保存
        TbUser user = new TbUser();
        user.setPhone(phone);
        user.setNickName(loginDTO.getNickName());
        user.setCreateTime(new Date()); // 设置创建时间
        user.setUpdateTime(new Date()); // 设置更新时间
        user.setPassword(DigestUtils.md5DigestAsHex(loginDTO.getPassword().getBytes()));
        userMapper.insert(user);
        return Result.success("注册成功");
    }

    @Override
    public Result<String> resetPassword(LoginDTO loginDTO) {
        // 1.校验手机号
        String phone = loginDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.error("手机号格式错误！");
        }
        // 2.校验验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginDTO.getCode();
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.error("验证码格式错误！");
        }
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.error("验证码错误");
        }

        // 3.查询用户是否存在
        TbUser user = userMapper.getByUsername(phone);
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

        // 校验文件大小 (例如 2MB)
        // if (file.getSize() > 2 * 1024 * 1024) {
        //     return Result.error("文件大小不能超过2MB");
        // }

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




