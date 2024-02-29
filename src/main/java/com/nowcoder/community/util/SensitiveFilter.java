package com.nowcoder.community.util;

import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Component
public class SensitiveFilter {

    private static final Logger logger= LoggerFactory.getLogger(SensitiveFilter.class);
    //替换符
    private  static final String REPLACEMENT="***";
    //根节点
    private TrieNode rootNode=new TrieNode();
    //根据敏感词文件初始化前缀树
    @PostConstruct//这个注解在这个SensitiveFilter在容器内初始化后调用（即服务启动时），只执行一次
    public void init(){
        try(InputStream is= this.getClass().getClassLoader().getResourceAsStream("sensitive-words.txt");
            BufferedReader reader=new BufferedReader(new InputStreamReader(is)))
        {
            String keyword;
            while ((keyword=reader.readLine())!=null){//每次读一行，因为在敏感词文件里也是一个词一行
                //添加到前缀树
                this.addKeyword(keyword);
            }
        } catch (IOException e){
            logger.error("加载敏感词文件失败"+e.getMessage());
        }
    }
    //将一个敏感词添加到前缀树中
    public void addKeyword(String keyword){
        TrieNode tempNode=rootNode;
        for(int i=0;i<keyword.length();i++){
            char c=keyword.charAt(i);
            TrieNode subNode= tempNode.getSubNode(c);
            if(subNode==null){
                //初始化子节点
                subNode=new TrieNode();
                tempNode.addSubNode(c,subNode);
            }
            tempNode=subNode;
            //设置结束标识
            if(i==keyword.length()-1){
                tempNode.setKeywordEnd(true);
            }
        }
    }
    //过滤敏感词的结果
    public String filter(String text){
        if(StringUtils.isBlank(text)){
            return null;
        }
        //三个指针
        TrieNode tempNode=rootNode;
        int begin=0;
        int position=0;
        //最后结果
        StringBuilder sb=new StringBuilder();
        while (position<text.length()){
            char c=text.charAt(position);
            //跳过符号
            if(isSymbol(c)){
                if(tempNode==rootNode){//若指针1处于根节点，则将此符号计入结果，让指针2向下走一步
                    sb.append(c);
                    begin++;
                }
                position++;//无论符号在开头还是中间，指针3都向下走一步
                continue;
            }
            //检查下一个字符
            tempNode=tempNode.getSubNode(c);
            if(tempNode==null){
                //这个字符在前缀树里面没找着，说明它不是敏感词
                sb.append(text.charAt(begin));
                //进入下一个位置
                position=++begin;
                tempNode=rootNode;//指针1归位
            } else if (tempNode.isKeywordEnd()) {
                //发现了敏感词，以begin开头，position结尾
                sb.append(REPLACEMENT);
                begin=++position;
                tempNode=rootNode;//指针1归位
            }else {
                //走到这都符合敏感词的条件，继续向下判断是不是敏感词
                position++;
            }
        }
        //指针3已经到终点，指针2没到终点的情况，把最后一波加进去
        sb.append(text.substring(begin));
        return sb.toString();
    }
    //判断是否为符号
    private boolean isSymbol(Character c){
        //0x2E80与0x9FFF之间的是东亚文字范围
        return !CharUtils.isAsciiAlphanumeric(c)&&(c<0x2E80||c>0x9FFF);
    }
    //前缀树
    private class TrieNode{
        //关键词结束标识
        private boolean isKeywordEnd=false;
        //当前节点的所有子节点(key是下级字符，value是下级节点)
        private Map<Character,TrieNode> subNodes=new HashMap<>();
        public boolean isKeywordEnd() {
            return isKeywordEnd;
        }

        public void setKeywordEnd(boolean keywordEnd) {
            isKeywordEnd = keywordEnd;
        }
        //添加子节点
        public void addSubNode(Character c,TrieNode node){
            subNodes.put(c,node);
        }
        //获取子节点
        public TrieNode getSubNode(Character c){
            return subNodes.get(c);
        }
    }
}
