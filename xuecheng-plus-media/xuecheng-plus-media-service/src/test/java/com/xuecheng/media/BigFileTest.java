package com.xuecheng.media;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//测试大文件上传的方法
public class BigFileTest {


    //测试文件分块方法
    @Test
    public void testChunk() throws IOException {
        File sourceFile = new File("G:/0B232BAF-5B0F-4756-A487-3E259855E72D.MOV");
        String chunkPath = "G:/chunk/";
        File chunkFolder = new File(chunkPath);
        if (!chunkFolder.exists()) {
            chunkFolder.mkdirs();
        }
        //分块大小
        long chunkSize = 1024 * 1024 * 5;
        //分块数量
        long chunkNum = (long) Math.ceil(sourceFile.length() * 1.0 / chunkSize);
        System.out.println("分块总数："+chunkNum);
        //缓冲区大小
        byte[] b = new byte[1024];
        //使用RandomAccessFile访问文件
        RandomAccessFile raf_read = new RandomAccessFile(sourceFile, "r");
        //分块
        for (int i = 0; i < chunkNum; i++) {
            //创建分块文件
            File file = new File(chunkPath + i);
            if(file.exists()){
                file.delete();
            }
            boolean newFile = file.createNewFile();
            if (newFile) {
                //向分块文件中写数据
                RandomAccessFile raf_write = new RandomAccessFile(file, "rw");
                int len = -1;
                while ((len = raf_read.read(b)) != -1) {
                    raf_write.write(b, 0, len);
                    if (file.length() >= chunkSize) {
                        break;
                    }
                }
                raf_write.close();
                System.out.println("完成分块"+i);
            }

        }
        raf_read.close();

    }

    //合并测试
    @Test
    public void testMere() throws IOException {
        //块文件目录
        File chunkFolder = new File("G:\\chunk\\");
        //合并后的文件
        File mergeFile = new File("G:/合并文件.MOV");

        File sourceFile = new File("G:/0B232BAF-5B0F-4756-A487-3E259855E72D.MOV");

        //取出所有的块
        File[] files = chunkFolder.listFiles();
        //将数组转成list
        List<File> fileList = Arrays.asList(files);

        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Integer.parseInt(o1.getName())-Integer.parseInt(o2.getName());
            }
        });
        //向合并文件写的流
        RandomAccessFile raf_rw = new RandomAccessFile(mergeFile,"rw");
        //缓冲区
        byte[] b = new byte[1024];
        //遍历分块文件，向合并文件写
        for (File file : fileList) {
            //读分块的流
            RandomAccessFile raf_r = new RandomAccessFile(file,"r");
            int len = -1;
            while ((len = raf_r.read(b)) != -1) {
                raf_rw.write(b, 0, len);
            }
            raf_r.close();
        }
        raf_rw.close();
        //合并完成后对合并的文件进行校验
        FileInputStream fileInputStream_merge = new FileInputStream(mergeFile);
        String md5_merge = DigestUtils.md5Hex(fileInputStream_merge);
        FileInputStream fileInputStream_source = new FileInputStream(sourceFile);
        String md5_source = DigestUtils.md5Hex(fileInputStream_source);
        if (md5_merge.equals(md5_source)){
            System.out.println("文件合并完成");
        }

    }
}
