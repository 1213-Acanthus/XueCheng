package com.xuecheng.media;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import io.minio.*;
import io.minio.errors.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

//测试minioSDK

public class MinioTest {
    // Create a minioClient with the MinIO server playground, its access key and secret key.
    static MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("http://192.168.101.65:9000")
                    .credentials("minioadmin", "minioadmin")
                    .build();

    @Test
    public void test_upload() throws Exception {
        //通过扩展名获得媒体资源类型mimetype
        //根据扩展名取出mimeType
        ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(".zip");
        String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;//通用mimeType，字节流
        if(extensionMatch!=null){
            mimeType = extensionMatch.getMimeType();
        }

        // Upload '/home/user/Photos/asiaphotos.zip' as object name 'asiaphotos-2015.zip' to bucket
        // 'asiatrip'.
        minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket("testbucket")
                        .object("讲义.zip")//在根目录创建文件
                        .object("teat/01/讲义.zip")//在子目录创建文件
                        .contentType(mimeType)//设置媒体文件类型
                        .filename("E:/讲义.zip")
                        .build());
        System.out.println("'E:/讲义.zip' is successfully uploaded as object '讲义.zip' to bucket 'testbucket'.");

    }
    @Test
    public void test_delete() throws Exception{
        RemoveObjectArgs removebucket = RemoveObjectArgs.builder()
                .bucket("testbucket")
                .object("讲义.zip")
                .build();
        minioClient.removeObject(removebucket);
        System.out.println("'E:/讲义.zip' is successfully remove from bucket 'testbucket'.");

    }

    @Test
    public void test_getFile() throws Exception{
        GetObjectArgs search_bucket = GetObjectArgs.builder().bucket("testbucket").object("teat/01/讲义.zip").build();
        FilterInputStream object = minioClient.getObject(search_bucket);
        //指定文件输出流
        FileOutputStream outputStream = new FileOutputStream(new File("D:/讲义.zip"));
        IOUtils.copy(object,outputStream);

        byte[] source_byteArray = toByteArray(object);

        //校验文件完整性 对文件的内容进行md5
        String source_md5 = DigestUtils.md5Hex(source_byteArray);

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File("D:/讲义.zip")));

        //取本地文件md5
        String local_md5 = DigestUtils.md5Hex(new FileInputStream(new File("D:/讲义.zip")));


        if(source_md5.equals(local_md5)){
            System.out.println("下载成功");
        }else{
            System.out.println("下载失败");
        }
    }

    public static byte[] toByteArray(FilterInputStream object) throws IOException {
        // 使用 FileInputStream 创建 BufferedInputStream
        try (BufferedInputStream bis = new BufferedInputStream(object)) {
            // 创建一个字节数组缓冲区，初始大小可以根据需要调整
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int bytesRead;
            // 读取文件内容到缓冲区，然后将缓冲区内容写入 ByteArrayOutputStream
            while ((bytesRead = bis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            // 将 ByteArrayOutputStream 转换为字节数组
            return baos.toByteArray();
        }
    }

    //将分块文件上传到minio
    @Test
    public void uploadChunk()throws Exception{
        for (int i=0;i<2;i++){
            UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                    .bucket("testbucket")
                    .object("chunk/"+i)//在根目录创建文件
//                    .contentType(mimeType)//设置媒体文件类型
                    .filename("G:/chunk/"+i)
                    .build();

            //上传文件
            minioClient.uploadObject(uploadObjectArgs);
            System.out.println("上传分块"+i+"成功");
        }
    }

    //调用minio接口 合并分块
    @Test
    public void testMerge()throws Exception{

        //
        List<ComposeSource> sources = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            //制定分块文件的信息
            ComposeSource source = ComposeSource.builder().bucket("testbucket").object("chunk/" + i).build();
            sources.add(source);
        }
        //制定合并后的objectname等信息
        ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder()
                .bucket("testbucket")
                .object("merge01.MOV")
                .sources(sources)
                .build();
        //合并文件
        minioClient.composeObject(composeObjectArgs);
    }

    //批量清理分块文件





}
