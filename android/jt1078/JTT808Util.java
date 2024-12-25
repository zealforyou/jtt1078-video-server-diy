package cn.zz.vido.jt1078;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JTT808Util {

    //实时视频数据包序号
    private static short RTP_VIDEO_COUNT = 0;
    //实时音频数据包序号
    private static short RTP_AUDIO_COUNT = 0;
    //计算这一I帧距离上一I帧的间隔
    private static long LAST_I_FRAME_TIME;
    //计算这一帧与上一帧的间隔
    private static long LAST_FRAME_TIME;

    public static synchronized void clearSq() {
        RTP_VIDEO_COUNT = 0;
        RTP_AUDIO_COUNT = 0;
    }
    /**
     * 打包实时视频RTP包
     *
     * @param data      G711Code数据
     * @param phone      SIM卡号
     * @param liveClient 与服务器的连接
     */
    public static synchronized void audioLiveG711a(byte[] data, int channelNum, String phone, LiveClient liveClient) {
        List<byte[]> dataList = new ArrayList<>();
        //每个包的大小
        double everyPkgSize = 950.d;
        int length = data.length;
        if (data.length > everyPkgSize) {
            //分包的总包数
            long totalPkg = Math.round(Math.ceil(length / everyPkgSize));
            for (int i = 1; i <= totalPkg; i++) {
                int end = (int) (i * everyPkgSize);
                if (end >= length) {
                    end = length;
                }
                byte[] bytes = Arrays.copyOfRange(data, (int) ((i - 1) * everyPkgSize), end);
                dataList.add(bytes);
            }
        } else {
            dataList.add(data);
        }
        for (int i = 0; i < dataList.size(); i++) {
            byte[] pkgData = dataList.get(i);
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            buffer.put(new byte[]{0x30, 0x31, 0x63, 0x64});//4
            //              V  P X  CC
            String vpxcc = "10 0 0 0001".replace(" ", "");
            buffer.put((byte) Integer.parseInt(vpxcc, 2));//5
            //            M    PT
            String mpt = "0 0000110".replace(" ", "");
            buffer.put((byte) Integer.parseInt(mpt, 2));//6
            //包序号
            buffer.put(ByteUtil.int2Word(RTP_AUDIO_COUNT));//8
            //SIM
            buffer.put(ByteUtil.appendBCD(phone, 6));//14
            //逻辑通道号
            buffer.put((byte) channelNum);//15
            String dataType = "";
            long currentTime  = System.currentTimeMillis();
            //音频类型
            dataType = "0011";
            //分包标记
            if (dataList.size() == 1) {
                //不分包
                dataType += "0000";
            } else if (i == 0) {
                //第一个包
                dataType += "0001";
            } else if (i == dataList.size() - 1) {
                //最后一个包
                dataType += "0010";
            } else {
                //中间包
                dataType += "0011";
            }
            //数据类型 分分包标记
            buffer.put((byte) Integer.parseInt(dataType, 2));//16
            //时间戳
            buffer.put(ByteUtil.long2Bytes(currentTime));//24
            //数据体长度
            buffer.put(ByteUtil.int2Word(pkgData.length));//26
            //数据体
            buffer.put(pkgData);

            //发送数据
            if (liveClient != null) {
                buffer.flip();
                byte[] sendData = new byte[buffer.remaining()];
                buffer.get(sendData);
                liveClient.sendData(sendData);
            }

            RTP_AUDIO_COUNT++;
        }
    }
    /**
     * 打包实时视频RTP包
     *
     * @param nalu       H.264一帧的视频数据
     * @param phone      SIM卡号
     * @param liveClient 与服务器的连接
     */
    public static synchronized void videoLive(byte[] sps, byte[] pps, byte[] nalu, int channelNum, String phone, LiveClient liveClient) {
        byte[] data = nalu;
        byte NALU = data[4];
        boolean isIFrame = (NALU & 0x1F) == 5;
        if (isIFrame) {
            data = new byte[nalu.length + sps.length + pps.length];
            System.arraycopy(sps, 0, data, 0, sps.length);
            System.arraycopy(pps, 0, data, sps.length, pps.length);
            System.arraycopy(nalu, 0, data, sps.length + pps.length, nalu.length);
        }
        List<byte[]> dataList = new ArrayList<>();
        //每个包的大小
        double everyPkgSize = 950.d;
        int length = data.length;
        if (data.length > everyPkgSize) {
            //分包的总包数
            long totalPkg = Math.round(Math.ceil(length / everyPkgSize));
            for (int i = 1; i <= totalPkg; i++) {
                int end = (int) (i * everyPkgSize);
                if (end >= length) {
                    end = length;
                }
                byte[] bytes = Arrays.copyOfRange(data, (int) ((i - 1) * everyPkgSize), end);
                dataList.add(bytes);
            }
        } else {
            dataList.add(data);
        }
        for (int i = 0; i < dataList.size(); i++) {
            byte[] pkgData = dataList.get(i);
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            buffer.put(new byte[]{0x30, 0x31, 0x63, 0x64});//4
            //              V  P X  CC
            String vpxcc = "10 0 0 0001".replace(" ", "");
            buffer.put((byte) Integer.parseInt(vpxcc, 2));//5
            //            M    PT
            String mpt = "0 1100010".replace(" ", "");
            buffer.put((byte) Integer.parseInt(mpt, 2));//6
            //包序号
            buffer.put(ByteUtil.int2Word(RTP_VIDEO_COUNT));//8
            //SIM
            buffer.put(ByteUtil.appendBCD(phone, 6));//14
            //逻辑通道号
            buffer.put((byte) channelNum);//15
            String dataType = "";
            //取h264的第5个字节，即NALU类型

            long currentTime  = System.currentTimeMillis();
            if (isIFrame) {
                //这是I帧
                dataType = "0000";
                LAST_I_FRAME_TIME = currentTime;
            } else {
                dataType = "0001";
            }
            //分包标记
            if (dataList.size() == 1) {
                //不分包
                dataType += "0000";
            } else if (i == 0) {
                //第一个包
                dataType += "0001";
            } else if (i == dataList.size() - 1) {
                //最后一个包
                dataType += "0010";
            } else {
                //中间包
                dataType += "0011";
            }
            //数据类型 分分包标记
            buffer.put((byte) Integer.parseInt(dataType, 2));//16
            //时间戳
            buffer.put(ByteUtil.long2Bytes(currentTime));//24
            //Last I Frame Interval
            long difIFrame = currentTime - LAST_I_FRAME_TIME;
            buffer.put(ByteUtil.int2Word(difIFrame));//26
            //Last Frame Interval
            if (LAST_FRAME_TIME == 0) {
                LAST_FRAME_TIME = currentTime;
            }
            long difFrame = currentTime - LAST_FRAME_TIME;
            buffer.put(ByteUtil.int2Word(difFrame));//28
            //数据体长度
            buffer.put(ByteUtil.int2Word(pkgData.length));//30
            //数据体
            buffer.put(pkgData);

            //发送数据
            if (liveClient != null) {
                buffer.flip();
                byte[] sendData = new byte[buffer.remaining()];
                buffer.get(sendData);
                Log.i("LiveClient", "videoSq:" + RTP_VIDEO_COUNT + "  size:" + sendData.length);
                liveClient.sendData(sendData);
            }
            RTP_VIDEO_COUNT++;
        }
        LAST_FRAME_TIME = System.currentTimeMillis();
    }
}
