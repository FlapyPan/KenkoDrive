package com.akagiyui.drive.model.response;

import com.akagiyui.drive.entity.Announcement;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 公告信息 响应
 *
 * @author AkagiYui
 */
@Data
@Accessors(chain = true)
public class AnnouncementResponse {

    /**
     * 公告id
     */
    private String id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 已启用
     */
    private boolean enabled;

    /**
     * 发布时间
     */
    private long createTime;

    /**
     * 修改时间
     */
    private long updateTime;

    public AnnouncementResponse(Announcement announcement) {
        this.id = announcement.getId();
        this.title = announcement.getTitle();
        this.content = announcement.getContent();
        this.userId = announcement.getAuthor().getId();
        this.username = announcement.getAuthor().getUsername();
        this.enabled = announcement.getEnabled();
        this.createTime = announcement.getCreateTime().getTime();
        this.updateTime = announcement.getUpdateTime().getTime();
    }

    /**
     * 从公告实体列表转换
     */
    public static List<AnnouncementResponse> fromAnnouncementList(List<Announcement> announcementList) {
        return announcementList.stream()
                .map(AnnouncementResponse::new)
                .toList();
    }
}
