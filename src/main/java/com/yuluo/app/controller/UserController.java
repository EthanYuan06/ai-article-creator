package com.yuluo.app.controller;

import com.mybatisflex.core.paginate.Page;
import com.yuluo.app.annotation.AuthCheck;
import com.yuluo.app.common.BaseResponse;
import com.yuluo.app.common.DeleteRequest;
import com.yuluo.app.common.ResultUtils;
import com.yuluo.app.constant.UserConstant;
import com.yuluo.app.exception.BusinessException;
import com.yuluo.app.exception.ErrorCode;
import com.yuluo.app.exception.ThrowUtils;
import com.yuluo.app.model.dto.user.UserAddRequest;
import com.yuluo.app.model.dto.user.UserLoginRequest;
import com.yuluo.app.model.dto.user.UserQueryRequest;
import com.yuluo.app.model.dto.user.UserRegisterRequest;
import com.yuluo.app.model.dto.user.UserUpdateRequest;
import com.yuluo.app.model.entity.User;
import com.yuluo.app.model.vo.LoginUserVO;
import com.yuluo.app.model.vo.UserVO;
import com.yuluo.app.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
@Tag(name = "用户接口")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    @Operation(summary = "获取当前登录用户")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    @Operation(summary = "用户注销")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户（管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "创建用户")
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        long result = userService.addUser(userAddRequest);
        return ResultUtils.success(result);
    }

    /**
     * 删除用户（管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "删除用户")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.deleteUser(deleteRequest);
        return ResultUtils.success(result);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @Operation(summary = "更新用户")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.updateUser(userUpdateRequest, request);
        return ResultUtils.success(result);
    }

    /**
     * 根据 ID 获取用户详情（管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "根据ID获取用户详情")
    public BaseResponse<User> getUserById(@RequestParam("id") Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getUserById(id);
        return ResultUtils.success(user);
    }

    /**
     * 根据 ID 获取用户视图对象
     */
    @GetMapping("/get/vo")
    @Operation(summary = "获取用户信息")
    public BaseResponse<UserVO> getUserVOById(@RequestParam("id") Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        UserVO userVO = userService.getUserVOById(id);
        return ResultUtils.success(userVO);
    }

    /**
     * 分页查询用户列表（视图对象）
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "分页查询用户列表")
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<UserVO> userVOPage = userService.listUserVOByPage(userQueryRequest);
        return ResultUtils.success(userVOPage);
    }

    /**
     * 上传用户头像
     */
    @PostMapping("/avatar/upload")
    @Operation(summary = "上传头像")
    public BaseResponse<Boolean> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestParam("id") Long id,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        
        // 校验权限：只能上传自己的头像，管理员可以上传任意用户头像
        User loginUser = userService.getLoginUser(request);
        if (!loginUser.getUserRole().equals(UserConstant.ADMIN_ROLE) && !loginUser.getId().equals(id)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权上传该用户头像");
        }
        
        boolean result = userService.uploadAvatar(file, id);
        return ResultUtils.success(result);
    }
}
