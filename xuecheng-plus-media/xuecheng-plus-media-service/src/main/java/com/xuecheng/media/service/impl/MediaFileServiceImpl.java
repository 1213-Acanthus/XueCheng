package com.xuecheng.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.media.config.MinioConfig;
import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.mapper.MediaProcessMapper;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileService;
import io.minio.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 * @date 2022/9/10 8:58
 */
@Slf4j
@Service
public class MediaFileServiceImpl implements MediaFileService {

    @Autowired
    MediaFilesMapper mediaFilesMapper;
    @Autowired
    MinioClient minioClient;
    @Autowired
    MinioConfig minioConfig;
    @Autowired
    MediaFileService currentProxy;
    //普通文件桶
    @Value("${minio.bucket.files}")
    private String files_bucket;
    @Autowired
    MediaProcessMapper mediaProcessMapper;


    @Override
    public MediaFiles getFileById(String mediaId) {
        return mediaFilesMapper.selectById(mediaId);
    }

    @Override
    public PageResult<MediaFiles> queryMediaFiels(Long companyId, PageParams pageParams, QueryMediaParamsDto queryMediaParamsDto) {

        //构建查询条件对象
        LambdaQueryWrapper<MediaFiles> queryWrapper = new LambdaQueryWrapper<>();

        //分页对象
        Page<MediaFiles> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        // 查询数据内容获得结果
        Page<MediaFiles> pageResult = mediaFilesMapper.selectPage(page, queryWrapper);
        // 获取数据列表
        List<MediaFiles> list = pageResult.getRecords();
        // 获取数据总数
        long total = pageResult.getTotal();
        // 构建结果集
        PageResult<MediaFiles> mediaListResult = new PageResult<>(list, total, pageParams.getPageNo(), pageParams.getPageSize());
        return mediaListResult;

    }

    //上传文件
    @Override
    public UploadFileResultDto uploadFile(Long companyId, UploadFileParamsDto uploadFileParamsDto, String localFilePath,String objectName) {
        //先得到扩展名
        //文件名
        String filename = uploadFileParamsDto.getFilename();
        //扩展名
        String extension = filename.substring(filename.lastIndexOf("."));
        String mimeType = getMimeType(extension);
        //获取桶
        //获取文件保存名
        String defaultFolderPath = getDefaultFolderPath();
        //拿到文件的md5值
        String fileMd5 = getFileMd5(new File(localFilePath));
        if(StringUtils.isEmpty(objectName)){
            //没有传入objectName就传拼接起来的
            //拿到objectName
            objectName = defaultFolderPath + fileMd5 + extension;
        }
        //上传文件到minio
        boolean result = addMediaFilesToMinIO(localFilePath, files_bucket, objectName, mimeType);
        if (!result) {
            XueChengPlusException.cast("上传文件失败！");
        }
        //入库文件信息
        MediaFiles mediaFiles = currentProxy.addMediaFilesToDb(companyId, fileMd5, uploadFileParamsDto, files_bucket, objectName);
        if (mediaFiles == null) {
            XueChengPlusException.cast("文件上传后保存信息失败");
        }
        //准备返回的对象
        UploadFileResultDto uploadFileResultDto = new UploadFileResultDto();
        BeanUtils.copyProperties(mediaFiles, uploadFileResultDto);


        return uploadFileResultDto;
    }

    //上传文件到minio
    public boolean addMediaFilesToMinIO(String localFilePath, String bucket, String objectName, String mimeType) {
        try {
            UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(mimeType)//设置媒体文件类型
                    .filename(localFilePath)
                    .build();
            //上传文件
            minioClient.uploadObject(uploadObjectArgs);
            log.debug("上传文件到minio成功，bucket:{},objectName:{}", bucket, objectName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            log.error("上传文件出错，bucket:{},objectName:{},错误信息：{}", bucket, objectName, e.getCause());
        }
        return false;
    }

    //根据扩展名获取mimetype
    private String getMimeType(String extension) {
        if (extension == null) {
            extension = "";
        }
        //将文件上传到minio
        ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(extension);
        String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;//通用mimeType，字节流
        if (extensionMatch != null) {
            mimeType = extensionMatch.getMimeType();
        }
        return mimeType;
    }

    //获取时间 对保存的项目的目录进行实现
    private String getDefaultFolderPath() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String folder = sdf.format(new Date()).replace("-", "/") + "/";
        return folder;
    }

    //获取文件的md5
    private String getFileMd5(File file) {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            String fileMd5 = DigestUtils.md5Hex(fileInputStream);
            return fileMd5;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @param companyId           机构id
     * @param fileMd5             文件md5值
     * @param uploadFileParamsDto 上传文件的信息
     * @param bucket              桶
     * @param objectName          对象名称
     * @return com.xuecheng.media.model.po.MediaFiles
     * @description 将文件信息添加到文件表
     * @author Mr.M
     * @date 2022/10/12 21:22
     */
    @Transactional
    public MediaFiles addMediaFilesToDb(Long companyId, String fileMd5, UploadFileParamsDto uploadFileParamsDto, String bucket, String objectName) {
        //将文件信息保存到数据库
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles == null) {
            //插入数据库
            mediaFiles = new MediaFiles();
            BeanUtils.copyProperties(uploadFileParamsDto, mediaFiles);
            //文件id
            mediaFiles.setId(fileMd5);
            //机构id
            mediaFiles.setCompanyId(companyId);
            //桶
            mediaFiles.setBucket(bucket);
            //file_path
            mediaFiles.setFilePath(objectName);
            //file_id
            mediaFiles.setFileId(fileMd5);
            //url
            mediaFiles.setUrl("/" + bucket + "/" + objectName);
            //上传时间
            mediaFiles.setCreateDate(LocalDateTime.now());
            //状态
            mediaFiles.setStatus("1");
            //审核状态
            mediaFiles.setAuditStatus("002003");
            //插入数据库
            int insert = mediaFilesMapper.insert(mediaFiles);
            if (insert <= 0) {
                log.debug("文件保存数据库失败，objectName:{},bucket:{}", objectName, bucket);
                return null;
            }
            //记录待处理任务
            //插入数据库是成功了才会插入 插入和待处理应该是一起成功或一起失败
            //是视频才写入待处理
            //向mediaprocess插入记录
            addWaitingTask(mediaFiles);
            return mediaFiles;
        }
        return mediaFiles;
    }

    //添加待处理任务
    public void addWaitingTask(MediaFiles mediaFiles) {
        //判断文件的mimetype
        String filename = mediaFiles.getFilename();
        //文件扩展名
        String extension = filename.substring(filename.indexOf("."));
        String mimeType = getMimeType(extension);
        if (mimeType == "video/quicktime") {
            //是mov类型
            MediaProcess mediaProcess = new MediaProcess();
            BeanUtils.copyProperties(mediaFiles, mediaProcess);
            //状态是未处理
            mediaProcess.setStatus("1");
            mediaProcess.setCreateDate(LocalDateTime.now());
            mediaProcess.setFailCount(0);//失败值默认为0
            mediaProcess.setUrl(null);
            mediaProcessMapper.insert(mediaProcess);

        }

    }

    @Override
    public RestResponse<Boolean> checkFile(String fileMd5) {
        //先查询数据库
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles != null) {
            //桶名字
            String bucket = mediaFiles.getBucket();
            //objectName
            String filePath = mediaFiles.getFilePath();
            //记录存在 查询minio
            GetObjectArgs search_bucket = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .build();
            try {
                FilterInputStream object = minioClient.getObject(search_bucket);
                if (object != null) {
                    //文件已经存在了
                    return RestResponse.success(true);
                }
            } catch (Exception e) {
                e.printStackTrace();

            }
        }
        //文件不存在
        return RestResponse.success(false);
    }

    @Override
    public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex) {
        //分块存储路径是，md5前两位为两个目录，chunk存储分块文件
        //根据md5得到分块存储的路径
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);

        //记录存在 查询minio
        GetObjectArgs search_bucket = GetObjectArgs.builder()
                .bucket(files_bucket)
                .object(chunkFileFolderPath + chunkIndex)
                .build();
        try {
            FilterInputStream object = minioClient.getObject(search_bucket);
            if (object != null) {
                //文件已经存在了
                return RestResponse.success(true);
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
        //文件不存在
        return RestResponse.success(false);
    }

    //上传分块到minio
    @Override
    public RestResponse uploadChunk(String fileMd5, int chunk, String localChunkFilePath) {
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5) + chunk;
        String mimeType = getMimeType(null);
        boolean b = addMediaFilesToMinIO(localChunkFilePath, files_bucket, chunkFileFolderPath, mimeType);
        if (!b) {
            return RestResponse.validfail(false, "上传分块文件失败");
        }
        //上传成功
        return RestResponse.success(true);
    }

    @Override
    public RestResponse mergechunks(Long companyId, String fileMd5, int chunkTotal, UploadFileParamsDto uploadFileParamsDto) {
        //找到分块文件 调用minio的sdk进行文件合并
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);//分块文件所在目录
        List<ComposeSource> sources = new ArrayList<>();
        for (int i = 0; i < chunkTotal; i++) {
            //制定分块文件的信息
            ComposeSource source = ComposeSource.builder().bucket(files_bucket).object(chunkFileFolderPath + i).build();
            sources.add(source);
        }
        String filename = uploadFileParamsDto.getFilename();
        String filePathByMd5 = getFilePathByMd5(fileMd5, filename.substring(filename.lastIndexOf(".")));
        //制定合并后的objectname等信息
        ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder()
                .bucket(files_bucket)
                .object(filePathByMd5)//合并后文件的名字
                .sources(sources)
                .build();
        //合并文件
        try {
            minioClient.composeObject(composeObjectArgs);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("合并文件出错，bucket:{},objectName:{},错误信息：{}", files_bucket, filePathByMd5, e.getMessage());
            return RestResponse.validfail(false, "合并文件异常");
        }


        //校验合并后的文件和源文件是否一致
        //下载文件
        File file = downloadFileFromMinIO(files_bucket, filePathByMd5);
        //计算合并后文件的md5
        FileInputStream FileInputStream = null;
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            String s = DigestUtils.md5Hex(fileInputStream);
            //比较原始的和合并后的md5值
            if (!fileMd5.equals(s)) {
                log.error("校验合并md5值不一致，原始文件：{}，合并文件：{}", fileMd5, s);
                return RestResponse.validfail(false, "文件校验失败");
            }
            //文件大小
            uploadFileParamsDto.setFileSize(file.length());
        } catch (Exception e) {
            return RestResponse.validfail(false, "文件校验失败");
        }
        //将文件信息入库
        MediaFiles mediaFiles = currentProxy.addMediaFilesToDb(companyId, fileMd5, uploadFileParamsDto, files_bucket, filePathByMd5);
        if (mediaFiles == null) {
            return RestResponse.validfail(false, "文件入库失败");
        }
        //清理分块文件
        clearChunkFiles(chunkFileFolderPath, chunkTotal);

        return RestResponse.success(true);


    }

    /**
     * 清除分块文件
     *
     * @param chunkFileFolderPath 分块文件路径
     * @param chunkTotal          分块文件总数
     */
    private void clearChunkFiles(String chunkFileFolderPath, int chunkTotal) {
        List<DeleteObject> objects = new ArrayList<>();
        for (int i = 0; i < chunkTotal; i++) {
            //制定分块文件的信息
            DeleteObject source = new DeleteObject(chunkFileFolderPath + i);
            objects.add(source);

        }
        RemoveObjectsArgs removebucket = RemoveObjectsArgs.builder()
                .bucket(files_bucket)
                .objects(objects)
                .build();
        Iterable<Result<DeleteError>> results = minioClient.removeObjects(removebucket);
        //想要真正删除需要遍历
        results.forEach(f -> {
            try {
                DeleteError deleteError = f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    //分块文件路径
    private String getChunkFileFolderPath(String fileMd5) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/chunk/";
    }

    /**
     * 得到合并后的文件的地址
     *
     * @param fileMd5 文件id即md5值
     * @param fileExt 文件扩展名
     * @return
     */
    private String getFilePathByMd5(String fileMd5, String fileExt) {
        return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/" + fileMd5 + fileExt;
    }

    //下载文件
    public File downloadFileFromMinIO(String bucket, String objectName) {
        //临时文件
        File minioFile = null;
        FileOutputStream outputStream = null;
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            //创建临时文件
            minioFile = File.createTempFile("minio", ".merge");
            outputStream = new FileOutputStream(minioFile);
            IOUtils.copy(stream, outputStream);
            return minioFile;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


}
