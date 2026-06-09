package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录命令
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22
 */
@Data
public class LoginCmd {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;
}
