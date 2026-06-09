package com.xiaomizhou.dpsk.controller.vo;

import com.xiaomizhou.dpsk.core.model.request.Page;
import lombok.Data;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/13 22:07
 * @description
 */
@Data
public class AgentHttpParam {


    @Data
    public static class Request extends Page {

        private String id;

        private String code;

        private String name;

        private String avatar;
    }

}
