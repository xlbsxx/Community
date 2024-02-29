package com.nowcoder.community.controller;

import com.nowcoder.community.entity.Comment;
import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.Event;
import com.nowcoder.community.event.EventProducer;
import com.nowcoder.community.service.CommentService;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.HostHolder;
import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@Controller
@RequestMapping("/comment")
public class CommentController implements CommunityConstant {
    @Autowired
    private CommentService commentService;
    @Autowired
    private HostHolder hostHolder;
    @Autowired
    private EventProducer eventProducer;
    @Autowired
    private DiscussPostService discussPostService;
    @Autowired
    private RedisTemplate redisTemplate;
    @RequestMapping(path = "/add/{discussPostId}" ,method = RequestMethod.POST)
    public String addComment(@PathVariable("discussPostId") int discussPostId, Comment comment){
        comment.setUserId(hostHolder.getUser().getId());
        comment.setStatus(0);
        comment.setCreateTime(new Date());
        commentService.addComment(comment);
        //在评论后，给被评论的发送系统消息
        Event event=new Event().setTopic(TOPIC_COMMENT).setUserId(hostHolder.getUser().getId())
                .setEntityType(comment.getEntityType()).setEntityId(comment.getEntityId())
                .setData("postId",discussPostId);
        if(comment.getEntityType()==ENTITY_TYPE_POST){//如果是帖子的评论
            //entityUserId存的是帖子的作者id
            DiscussPost discussPost=discussPostService.findDiscussPostById(comment.getEntityId());
            event.setEntityUserId(discussPost.getUserId());//找到帖子的作者id
        } else if (comment.getEntityType()==ENTITY_TYPE_COMMENT) {//如果评论的是别人的评论
            //entityUserId存的是评论的作者id
            Comment comment1=commentService.findCommentById(comment.getEntityId());
            event.setEntityUserId(comment1.getUserId());//找到评论的作者id
        }
        eventProducer.fireEvent(event);

        //帖子被评论后，帖子的内容也有了变化，要重新提交给es服务器
        if(comment.getEntityType()==ENTITY_TYPE_POST){
            event=new Event().setTopic(TOPIC_PUBLISH).setUserId(hostHolder.getUser().getId())
                    .setEntityType(ENTITY_TYPE_POST).setEntityId(discussPostId);
            eventProducer.fireEvent(event);

            // 计算帖子分数，把帖子存到redis缓存里面，后面统一计算
            String redisKey = RedisKeyUtil.getPostScoreKey();
            redisTemplate.opsForSet().add(redisKey, discussPostId);
        }

        return "redirect:/discuss/detail/"+discussPostId;//重定向回帖子详情界面
    }
}
