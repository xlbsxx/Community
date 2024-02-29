package com.nowcoder.community.controller;

import com.nowcoder.community.entity.*;
import com.nowcoder.community.event.EventProducer;
import com.nowcoder.community.service.CommentService;
import com.nowcoder.community.service.DiscussPostService;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.HostHolder;
import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

@Controller
@RequestMapping("/discuss")
public class DiscussPostController implements CommunityConstant {
    @Autowired
    private DiscussPostService discussPostService;
    @Autowired
    private HostHolder hostHolder;
    @Autowired
    private UserService userService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private LikeService likeService;
    @Autowired
    private EventProducer eventProducer;
    @Autowired
    private RedisTemplate redisTemplate;
    @RequestMapping(path = "/add", method= RequestMethod.POST)
    @ResponseBody
    public String addDiscussPost(String title,String content){
        User user=hostHolder.getUser();
        if(user==null){
            return CommunityUtil.getJSONString(403,"你还没有登录!");
        }
        DiscussPost post=new DiscussPost();
        post.setUserId(user.getId());
        post.setTitle(title);
        post.setContent(content);
        post.setCreateTime(new Date());
        discussPostService.addDiscussPost(post);

        //把新发布的帖子存到es服务器中
        Event event=new Event().setTopic(TOPIC_PUBLISH).setUserId(user.getId())
                .setEntityType(ENTITY_TYPE_POST).setEntityId(post.getId());
        eventProducer.fireEvent(event);

        // 计算帖子分数,把帖子存到redis缓存里面，后面统一计算
        String redisKey = RedisKeyUtil.getPostScoreKey();
        redisTemplate.opsForSet().add(redisKey, post.getId());


        return CommunityUtil.getJSONString(0,"发布成功！");
    }
    @RequestMapping(path = "/detail/{discussPostId}", method= RequestMethod.GET)
    public String getDiscussPost(@PathVariable("discussPostId")int discussPostId, Model model, Page page){
        //找到帖子
        DiscussPost post=discussPostService.findDiscussPostById(discussPostId);
        model.addAttribute("post",post);
        //根据帖子id找到对应用户
        User user=userService.findUserById(post.getUserId());
        model.addAttribute("user",user);
        //帖子的赞数量
        long likeCount=likeService.findEntityLikeCount(ENTITY_TYPE_POST,discussPostId);
        model.addAttribute("likeCount",likeCount);
        //当前用户是否给帖子点赞（先判断用户登录没有，没登录自然没有点赞）
        int likeStatus=hostHolder.getUser()==null?0:likeService.findEntityLikeStatus(hostHolder.getUser().getId(),ENTITY_TYPE_POST,discussPostId);
        model.addAttribute("likeStatus",likeStatus);
        //评论分页信息
        page.setLimit(5);//每页显示五条
        page.setPath("/discuss/detail/"+discussPostId);
        page.setRows(post.getCommentCount());//一共多少评论,再结合limit,就可以算出一共需要多少页
        //评论：帖子的评论
        //回复：给评论的评论
        //评论列表
        List<Comment> commentList=commentService.findCommentsByEntity(ENTITY_TYPE_POST,post.getId(), page.getOffset(), page.getLimit());
        //评论VO列表(view object)
        List<Map<String,Object>>commentVOList=new ArrayList<>();
        if(commentList!=null){
            for(Comment comment:commentList){//遍历每一个评论
                Map<String,Object>commentVO=new HashMap<>();
                //评论的内容
                commentVO.put("comment",comment);
                //根据评论的userId找到评论的用户
                commentVO.put("user",userService.findUserById(comment.getUserId()));
                //评论的赞数量
                likeCount=likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT,comment.getId());
                commentVO.put("likeCount",likeCount);
                //当前用户是否给评论点赞（先判断用户登录没有，没登录自然没有点赞）
                likeStatus=hostHolder.getUser()==null?0:likeService.findEntityLikeStatus(hostHolder.getUser().getId(),ENTITY_TYPE_COMMENT,comment.getId());
                commentVO.put("likeStatus",likeStatus);
                //评论的回复
                List<Comment> replyList=commentService.findCommentsByEntity(ENTITY_TYPE_COMMENT,comment.getId(),0,Integer.MAX_VALUE);//评论就不分页了
                //回复VO列表(view object)
                List<Map<String,Object>>replyVOList=new ArrayList<>();
                if(replyList!=null){
                    for(Comment reply:replyList){
                        Map<String,Object>replyVO=new HashMap<>();
                        //回复
                        replyVO.put("reply",reply);
                        //回复的作者
                        replyVO.put("user",userService.findUserById(reply.getUserId()));
                        //回复了谁
                        User target= reply.getTargetId()==0?null:userService.findUserById(reply.getTargetId());
                        replyVO.put("target",target);
                        //回复的赞数量
                        likeCount=likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT,reply.getId());
                        replyVO.put("likeCount",likeCount);
                        //当前用户是否给回复点赞（先判断用户登录没有，没登录自然没有点赞）
                        likeStatus=hostHolder.getUser()==null?0:likeService.findEntityLikeStatus(hostHolder.getUser().getId(),ENTITY_TYPE_COMMENT,reply.getId());
                        replyVO.put("likeStatus",likeStatus);

                        replyVOList.add(replyVO);
                    }
                }
                commentVO.put("replys",replyVOList);
                //每个评论回复的数量
                int replyCount=commentService.findCommentCount(ENTITY_TYPE_COMMENT,comment.getId());
                commentVO.put("replyCount",replyCount);

                commentVOList.add(commentVO);
            }
        }
        model.addAttribute("comments",commentVOList);
        return "/site/discuss-detail";
    }

    // 置顶
    @RequestMapping(path = "/top", method = RequestMethod.POST)
    @ResponseBody
    public String setTop(int id) {
        discussPostService.updateType(id, 1);
        //置顶完了，帖子数据有变，也要将变化同步到es服务器里面
        // 触发发帖事件
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        return CommunityUtil.getJSONString(0);
    }

    // 加精
    @RequestMapping(path = "/wonderful", method = RequestMethod.POST)
    @ResponseBody
    public String setWonderful(int id) {
        discussPostService.updateStatus(id, 1);

        // 触发发帖事件
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        // 计算帖子分数，把帖子存到redis缓存里面，后面统一计算
        String redisKey = RedisKeyUtil.getPostScoreKey();
        redisTemplate.opsForSet().add(redisKey, id);

        return CommunityUtil.getJSONString(0);
    }

    // 删除
    @RequestMapping(path = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public String setDelete(int id) {
        discussPostService.updateStatus(id, 2);

        // 触发删帖事件，从es服务器中把帖子删掉
        Event event = new Event()
                .setTopic(TOPIC_DELETE)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        return CommunityUtil.getJSONString(0);
    }

}
