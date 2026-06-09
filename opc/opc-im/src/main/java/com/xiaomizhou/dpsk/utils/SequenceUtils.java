package com.xiaomizhou.dpsk.utils;

import java.util.UUID;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/20 14:45
 * @description
 */
public class SequenceUtils {


    private SequenceUtils() {

    }

    private static final UUIDSequenceGenerator UUID_SEQUENCE_GENERATOR = new UUIDSequenceGenerator();

    public static UUIDSequenceGenerator generator() {
        return UUID_SEQUENCE_GENERATOR;
    }


    /**
     *
     */
    public static class UUIDSequenceGenerator implements SequenceGenerator<String> {

        public static final String CONVERSATION_PREFIX = "CONV";

        public static final String CHAT_MESSAGE_PREFIX = "MSG";

        public static final String TOKEN_USAGE_PREFIX = "TKU";

        public static final String CHAT_GROUP_PREFIX = "GRP";

        public static final String CHAT_GROUP_MEMBER_PREFIX = "GRM";

        public static final String AGENT_PREFIX = "AGT";

        public static final String TOKEN_PREFIX = "TKN";

        public static final String TASK_PREFIX = "TSK";

        private UUIDSequenceGenerator() {
        }

        @Override
        public String next() {
            return UUID.randomUUID().toString().replace("-", "");
        }

        @Override
        public String next(Object prefix) {
            return String.valueOf(prefix).toUpperCase() + "-" + next();
        }
    }


    interface SequenceGenerator<T> {

        /**
         *
         * @return
         */
        T next();

        /**
         *
         * @param prefix
         * @return
         */
        T next(Object prefix);
    }

}
