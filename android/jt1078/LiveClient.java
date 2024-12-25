package cn.zz.vido.jt1078;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



/**
 * 项目名:    JTTProtocol
 * 包名       com.azhon.jtt808.netty.live
 * 文件名:    LiveClient
 * 创建时间:  2020/2/26 on 20:51
 * 描述:     TODO 视频监控推流
 *
 * @author 阿钟
 */

public class LiveClient {

    private static final String TAG = "LiveClient";

    private String ip;
    private int port;
    private Socket socket;
    private OutputStream outputStream;

    private static ExecutorService es = Executors.newSingleThreadExecutor();

    public LiveClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
        es.execute(() -> connect());
    }

    /**
     * 初始化连接
     */
    private void connect() {
        try {
            socket = new Socket(ip, port);
            outputStream = socket.getOutputStream();
            Log.d(TAG, "实时监控服务器连接成功");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "实时监控服务器连接失败：" + e.getMessage());
        }
    }

    /**
     * 发送数据
     *
     * @param data
     */
    public synchronized void sendData(byte[] data) {
        es.execute(() -> {
            if (outputStream != null) {
                try {
//                    Log.i(TAG, "live:" + DataUtils.bytesToHex(data));
                    outputStream.write(data);
                } catch (IOException e) {
                    Log.d(TAG, "实时发送视频数据失败: " + e.getLocalizedMessage());
                }
            }
        });
    }

    public void release() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
