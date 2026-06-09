package com.xiaomizhou.dpsk.db.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册命令
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22
 */
@Data
public class RegisterCmd {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;

    @NotBlank(message = "名称不能为空")
    private String name;

    /**
     * 昵称，可选
     */
    private String nickname;
}
