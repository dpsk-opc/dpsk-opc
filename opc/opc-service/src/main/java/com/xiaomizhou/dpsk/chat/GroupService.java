//package com.xiaomizhou.dpsk.chat;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.tuple.ImmutablePair;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
///**
// * @author eason - vipzhsh@163.com
// * @date 2026/5/14 15:29
// * @description
// */
//@RequiredArgsConstructor
//@Component
//@Slf4j
//public class GroupService {
//
//
//
//
////    /**
////     * 获取群组列表
////     *
////     * @param pageNo
////     * @param pageSize
////     * @return
////     */
////    public ImmutablePair<Long, List<Group>> getGroups(int pageNo, int pageSize) {
////        return new ImmutablePair<>((long) groupDAO.countAllGroups(), groupDAO.listAllGroups(pageSize, pageNo));
////    }
////
////    /**
////     * 创建新群组
////     *
////     * @param name
////     * @param ownerId
////     * @param members
////     * @return
////     */
////    public Group newGroup(String name, String ownerId, List<String> members) {
////        Group group = new Group();
////        group.setName(name);
////        group.setOwnerId(ownerId);
////        group.setMembers(members);
////        groupDAO.insert(group);
////        return group;
////    }
//
//}
