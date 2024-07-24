package com.xuecheng.media.service;

import com.xuecheng.media.model.po.MediaProcess;

import java.util.List;

public interface MediaFileProcessService {
    public List<MediaProcess> getMediaProcessList(int shardTotal,int shardIndex,int count);

    public  boolean strartTask(long id);
    public void saveProcessFinishStatus(Long taskId,String status,String fileId,String url,String errorMsg);
}
