package com.xiaomizhou.dpsk.utils;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;
import java.util.Objects;

public class MemoryUtils {

    private MemoryUtils(){}


    public static String toSingleContent(UserMessage msg){

        if(Objects.isNull(msg)){
            return "";
        }

        List<Content> contents = msg.contents();

        if(Objects.nonNull(contents) && 1 == contents.size()) {
            return "用户: " + msg.singleText();
        }
        StringBuilder sb  = new StringBuilder("用户: ");
        for (Content content : contents) {

            if(content instanceof TextContent){
                sb.append(((TextContent) content).text());
            }
        }
        return sb.toString();
    }

}
