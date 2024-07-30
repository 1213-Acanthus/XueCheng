package com.xuecheng.content.feignclient;

import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SearchServiceClientFallbackFactory implements FallbackFactory<SearchServiceClient> {


    @Override
    public SearchServiceClient create(Throwable throwable) {
        return new SearchServiceClient() {
            @Override
            public Boolean add(CourseIndex courseIndex) {
                //熔断降级
                log.debug("添加课程索引发生熔断，索引的信息：{},熔断的异常信息：{}",courseIndex,throwable);
                //走降级返回false
                return false;
            }
        };
    }
}
