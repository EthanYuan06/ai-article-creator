package com.yuluo.app.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.yuluo.app.common.DeleteRequest;
import com.yuluo.app.model.dto.user.UserAddRequest;
import com.yuluo.app.model.dto.user.UserQueryRequest;
import com.yuluo.app.model.dto.user.UserUpdateRequest;
import com.yuluo.app.model.entity.User;
import com.yuluo.app.model.vo.LoginUserVO;
import com.yuluo.app.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

public interface UserService extends IService<User> {

    long userRegister(String userAccount, String userPassword, String checkPassword);

    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    User getLoginUser(HttpServletRequest request);

    boolean userLogout(HttpServletRequest request);

    long addUser(UserAddRequest userAddRequest);

    boolean updateUser(UserUpdateRequest userUpdateRequest, HttpServletRequest request);

    boolean deleteUser(DeleteRequest deleteRequest);

    LoginUserVO getLoginUserVO(User user);

    String getEncryptPassword(String userPassword);

    User getUserById(Long id);

    UserVO getUserVOById(Long id);

    Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest);

    boolean uploadAvatar(MultipartFile file, Long userId);
}
