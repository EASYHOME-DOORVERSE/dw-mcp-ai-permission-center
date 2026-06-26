package com.easyhome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easyhome.entity.SysApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysApiKeyMapper extends BaseMapper<SysApiKey> {

    /**
     * 根据 API Key 值查询完整的 Key 记录（含用户信息联查）
     */
    @Select("SELECT * FROM sys_api_key WHERE api_key = #{apiKey} LIMIT 1")
    SysApiKey selectByApiKey(@Param("apiKey") String apiKey);

    /**
     * 更新最近使用时间
     */
    @Update("UPDATE sys_api_key SET last_used_at = NOW() WHERE api_key = #{apiKey}")
    void updateLastUsedAt(@Param("apiKey") String apiKey);
}
