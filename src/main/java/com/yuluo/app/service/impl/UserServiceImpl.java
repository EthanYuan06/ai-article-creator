package com.yuluo.app.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yuluo.app.common.DeleteRequest;
import com.yuluo.app.exception.BusinessException;
import com.yuluo.app.exception.ErrorCode;
import com.yuluo.app.exception.ThrowUtils;
import com.yuluo.app.mapper.UserMapper;
import com.yuluo.app.model.dto.user.UserAddRequest;
import com.yuluo.app.model.dto.user.UserQueryRequest;
import com.yuluo.app.model.dto.user.UserUpdateRequest;
import com.yuluo.app.model.entity.User;
import com.yuluo.app.model.enums.UserRoleEnum;
import com.yuluo.app.model.vo.LoginUserVO;
import com.yuluo.app.model.vo.UserVO;
import com.yuluo.app.service.CosService;
import com.yuluo.app.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.yuluo.app.constant.UserConstant.USER_LOGIN_STATE;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private CosService cosService;

    @Value("${PASSWORD_SALT}")
    private String salt;

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入账号或密码");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度至少4个字符");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度至少8个字符");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 账号查重，查询用户是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
        // 3. 密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 创建用户，插入数据
        User user = User.builder()
                .userAccount(userAccount)
                .userPassword(encryptPassword)
                .userName("默认用户")
                .userRole(UserRoleEnum.USER.getValue())
                .build();
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        // 5. 返回用户 id
        return user.getId();
    }

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      登录态
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入账号或密码");
        }
        // 2. 给密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 通过账号密码，查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 4. 记录用户登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 5. 返回用户信息
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前登录用户
     *
     * @param request 登录态
     * @return 当前登录用户
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查最新用户信息
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 用户注销
     *
     * @param request 登录态
     * @return 注销成功状态
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request.getSession().getAttribute(USER_LOGIN_STATE) == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    /**
     * 创建用户（管理员）
     *
     * @param userAddRequest 创建用户参数
     * @return 新用户信息
     */
    @Override
    public long addUser(UserAddRequest userAddRequest) {
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        //默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        user.setUserPassword(getEncryptPassword(DEFAULT_PASSWORD));
        boolean save = this.save(user);
        if (!save) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建用户失败");
        }
        return user.getId();
    }

    /**
     * 更新用户信息
     *
     * @param userUpdateRequest 更新用户参数
     * @return 更新成功状态
     */
    @Override
    public boolean updateUser(UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        // 1. 校验参数
        if (userUpdateRequest.getId() == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        }
        // 2. 校验身份，管理员能修改任意用户信息，用户只能修改本账号信息
        User loginUser = this.getLoginUser(request);
        if (!loginUser.getUserRole().equals(UserRoleEnum.ADMIN.getValue()) &&
                !loginUser.getId().equals(userUpdateRequest.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean update = this.updateById(user);
        if (!update) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新用户失败");
        }
        return true;
    }

    /**
     * 删除用户（管理员）
     *
     * @param deleteRequest 删除用户参数
     * @return 删除成功状态
     */
    @Override
    public boolean deleteUser(DeleteRequest deleteRequest) {
        if (deleteRequest.getId() == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        }
        if (deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户id非法");
        }
        return this.removeById(deleteRequest.getId());
    }

    /**
     * 获取当前登录用户
     *
     * @param user 用户
     * @return 当前登录用户
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 获取加密密码
     *
     * @param userPassword 密码
     * @return 加密密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        return DigestUtils.md5DigestAsHex((salt + userPassword).getBytes());
    }

    /**
     * 根据 ID 获取用户详情
     *
     * @param id 用户 ID
     * @return 用户实体
     */
    @Override
    public User getUserById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        User user = this.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return user;
    }

    /**
     * 根据 ID 获取用户视图对象
     *
     * @param id 用户 ID
     * @return 用户视图对象
     */
    @Override
    public UserVO getUserVOById(Long id) {
        User user = this.getUserById(id);
        return this.getUserVO(user);
    }

    /**
     * 分页查询用户列表（视图对象）
     *
     * @param userQueryRequest 查询参数
     * @return 分页用户列表
     */
    @Override
    public Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        
        QueryWrapper queryWrapper = this.getQueryWrapper(userQueryRequest);
        
        // 分页查询
        Page<User> userPage = this.page(new Page<>(current, size), queryWrapper);
        
        // 转换为 VO
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotalPage());
        List<UserVO> userVOList = this.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        
        return userVOPage;
    }

    /**
     * 上传用户头像
     *
     * @param file   上传的文件
     * @param userId 用户ID
     * @return 上传成功状态
     */
    @Override
    public boolean uploadAvatar(MultipartFile file, Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        
        // 1. 校验文件
        validatePicture(file);
        
        File tempFile = null;
        try {
            // 2. 转换为临时File
            String originalFilename = file.getOriginalFilename();
            String suffix = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf('.')) 
                    : ".png";
            tempFile = File.createTempFile("upload_", suffix);
            file.transferTo(tempFile);
            
            // 3. 构建上传路径
            String uploadPathPrefix = "avatar/" + userId;
            
            // 4. 调用CosService上传
            String url = cosService.uploadFile(tempFile, uploadPathPrefix);
            
            if (url == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
            }

            // 5. 更新数据库
            User user = new User();
            user.setId(userId);
            user.setUserAvatar(url);
            boolean result = updateById(user);
            if (!result){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新头像失败");
            }
            return true;
        } catch (Exception e) {
            log.error("上传头像失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 校验图片文件
     *
     * @param file 上传的文件
     */
    private void validatePicture(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 校验文件大小（5MB）
        long fileSize = file.getSize();
        if (fileSize > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过5MB");
        }

        // 校验文件格式
        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase()
                : "";
        List<String> allowedFormats = Arrays.asList("jpg", "jpeg", "png", "webp");
        if (!allowedFormats.contains(suffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的图片格式");
        }
    }

    /**
     * 构建查询条件
     *
     * @param userQueryRequest 查询参数
     * @return 查询条件
     */
    private QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        QueryWrapper queryWrapper = new QueryWrapper();

        // 模糊查询用户昵称
        if (StrUtil.isNotBlank(userQueryRequest.getUserName())) {
            queryWrapper.like("userName", userQueryRequest.getUserName());
        }

        // 精确查询用户角色
        if (StrUtil.isNotBlank(userQueryRequest.getUserRole())) {
            queryWrapper.eq("userRole", userQueryRequest.getUserRole());
        }

        // 排序处理
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        if (StrUtil.isNotBlank(sortField)) {
            boolean isAsc = "ascend".equals(sortOrder);
            queryWrapper.orderBy(sortField, isAsc);
        } else {
            // 默认按创建时间降序
            queryWrapper.orderBy("createTime", false);
        }

        return queryWrapper;
    }

    /**
     * 批量转换用户实体为视图对象
     *
     * @param userList 用户实体列表
     * @return 用户视图对象列表
     */
    private List<UserVO> getUserVOList(List<User> userList) {
        if (userList == null || userList.isEmpty()) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .toList();
    }

    /**
     * 获取用户视图对象（脱敏）
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    private UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }
}
