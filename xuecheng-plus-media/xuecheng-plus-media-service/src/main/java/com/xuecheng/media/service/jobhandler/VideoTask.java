package com.xuecheng.media.service.jobhandler;

import com.xuecheng.base.utils.Mp4VideoUtil;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileProcessService;
import com.xuecheng.media.service.MediaFileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class VideoTask {
    @Autowired
    MediaFileProcessService mediaFileProcessService;

    @Autowired
    MediaFileService mediaFileService;

    @Value("${videoprocess.ffmpegpath}")
    private String ffmpegpath;

    /**
     * 2、分片广播任务
     */
    @XxlJob("videoJobHandler")
    public void shardingJobHandler() throws Exception {

        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();//执行器序号
        int shardTotal = XxlJobHelper.getShardTotal();//执行器总数

        //确定cpu的核心数
        int processors = Runtime.getRuntime().availableProcessors();

        //查询待处理的任务
        List<MediaProcess> mediaProcessList = mediaFileProcessService.getMediaProcessList(shardTotal, shardIndex, processors);
        //任务数量为list.size
        int size = mediaProcessList.size();
        if (!(size > 0)) {
            log.debug("取到的视频处理任务数：{}", size);
            return;
        }
        //启动线程池
        ExecutorService executorService = Executors.newFixedThreadPool(size);
        //使用计数器
        CountDownLatch countDownLatch = new CountDownLatch(size);
        mediaProcessList.forEach(mediaProcess -> {
            //将任务加入线程池
            executorService.execute(() -> {
                try {
                    //任务执行逻辑
                    Long id = mediaProcess.getId();
                    String bucket = mediaProcess.getBucket();
                    String filePath = mediaProcess.getFilePath();
                    //文件id（md5）
                    String fileId = mediaProcess.getFileId();
                    //抢任务
                    boolean b = mediaFileProcessService.strartTask(id);
                    if (!b) {
                        log.debug("抢占任务失败，任务id：{}", id);
                        return;
                    }
                    //成功
                    //下载minio上的视频到本地
                    File file = mediaFileService.downloadFileFromMinIO(bucket, filePath);

                    if (file == null) {
                        log.debug("下载视频出错，任务id:{},bucket:{},objectname:{}", id, bucket, filePath);
                        //保存任务处理失败的结果
                        mediaFileProcessService.saveProcessFinishStatus(id, "3", fileId, null, "下载视频到本地失败");
                        return;
                    }

                    //执行视频的转码
                    String ffmpeg_path = ffmpegpath;//ffmpeg的安装位置
                    //源视频的路径
                    String video_path = file.getAbsolutePath();
                    //转换后mp4文件的名称
                    String mp4_name = fileId + ".mp4";
                    //转换后mp4文件的路径
                    //先创建一个临时文件，作为转换后的文件
                    File minio = null;
                    try {
                        minio = File.createTempFile("minio", ".mp4");
                    } catch (IOException e) {
                        log.debug("创建临时文件异常，{}", e.getMessage());
                        //保存任务处理失败的结果
                        mediaFileProcessService.saveProcessFinishStatus(id, "3", fileId, null, "创建临时文件异常");
                        return;
                    }
                    String mp4_path = minio.getAbsolutePath();
                    //创建工具类对象
                    Mp4VideoUtil videoUtil = new Mp4VideoUtil(ffmpeg_path, video_path, mp4_name, mp4_path);
                    //开始视频转换，成功将返回success
                    String s = videoUtil.generateMp4();
                    if (!s.equals("success")) {
                        log.debug("视频转码失败，bucket:{},objectName:{},原因：{}", bucket, filePath, s);
                        //保存任务处理失败的结果
                        mediaFileProcessService.saveProcessFinishStatus(id, "3", fileId, null, "视频转码失败");
                        return;
                    }
                    //上传到minio
                    String object  = getFilePathByMd5(fileId,".mp4");
                    boolean b1 = mediaFileService.addMediaFilesToMinIO(mp4_path, bucket, object, "video/mp4");
                    if (!b1) {
                        log.debug("上传mp4到minio失败，taskid：{}", id);
                        //保存任务处理失败的结果
                        mediaFileProcessService.saveProcessFinishStatus(id, "3", fileId, null, "上传mp4到minio失败");

                        return;
                    }
                    //mp4文件的url
                    String url = getFilePathByMd5(fileId, ".mp4");
                    //保存任务状态为成功
                    mediaFileProcessService.saveProcessFinishStatus(id, "2", fileId, url, null);
                    minio.delete();
                    //保存任务的处理结果
                } finally {
                    //计数器减一
                    countDownLatch.countDown();
                }
            });
        });
        //阻塞
        countDownLatch.await(30, TimeUnit.MINUTES);//制定最大限度的等待时间 一旦出现断电等情况计数器无法归零 那么等指定时间就继续下一轮


    }

    private String getFilePathByMd5(String fileMd5, String fileExt) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/" + fileMd5 + fileExt;
    }

}
