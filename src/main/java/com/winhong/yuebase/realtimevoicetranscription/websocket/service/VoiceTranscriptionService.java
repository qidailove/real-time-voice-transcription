package com.winhong.yuebase.realtimevoicetranscription.websocket.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.winhong.yuebase.realtimevoicetranscription.iflytek.DraftWithOrigin;
import com.winhong.yuebase.realtimevoicetranscription.iflytek.utils.EncryptUtil;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.util.ByteBufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;


@Component
@ServerEndpoint("/voice/real/transcribe/{userId}")
public class VoiceTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(VoiceTranscriptionService.class);

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss");

    /**
     * 限制连接客户端个数
     */
    private static final int MAX_CONNETE_COUNT = 5;

    public VoiceTranscriptionService.MyWebSocketClient client = null;

    private Session session;

    private String userId;

    // appid
    private static final String APPID = "cbcd4ebe";

    // appid对应的secret_key
    private final String SECRET_KEY = "2d6f62a84a7779a455ce0eda09f6a14a";

    // 请求地址
    private final String HOST = "rtasr.xfyun.cn/v1/ws";

    private final String BASE_URL = "wss://" + HOST;

    private final String ORIGIN = "https://" + HOST;

    // 每次发送的数据大小 1280 字节
    private final int CHUNCKED_SIZE = 1280;

    private String sid = null;

    private CountDownLatch connectClose;

    private volatile boolean isSuspend = false;

    /**
     * 记录连接本服务的客户端
     */
    private static Map<String, Session> localConnectClientInfoMap = new ConcurrentHashMap<>();

    /**
     * 记录连接科大讯飞的客户端
     */
    private static volatile Map<String,Session> remoteConnectIflyClientInfoMap = new ConcurrentHashMap<>();

    /**
     * 最大连接数判断
     * @return
     */
    public boolean overConnectCountLimit(){
        Semaphore semaphore = new Semaphore(1);
        try {
            semaphore.acquire();
            if (localConnectClientInfoMap.size() >= MAX_CONNETE_COUNT){
                log.info("=========>已超过本地连接人数，连接关闭！");
                return true;
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            semaphore.release();
        }
        return false;
    }

    /**
     * 关闭前端的websocket连接
     */
    public void closeWebConnect(){
        onClose(userId);
    }

    /**
     * 关闭连接科大讯飞服务器
     */
    public void closeConnectIflyServer(){
        if (Objects.nonNull(client)){
            try {
                // 发送结束标识
                client.send("{\"end\": true}".getBytes());
                log.info( "==============>成功向科大讯飞服务器发送结束标识{\"end\": true}");
                connectClose.await();
                log.info("==========>已关闭与科大讯飞服务器的连接！");
            }catch (Exception e){
                e.printStackTrace();
                log.error("==============>关闭与科大讯飞服务器的连接出错！");
            }
        }
    }

    /**
     *
     * 连接触发方法
     * @param session
     * @param userId
     * @return
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId){
        this.session = session;
        this.userId = userId;
        /**
         * 限制人数连接
         */
        if (overConnectCountLimit()){
            closeWebConnect();
            return;
        }
        //1.连接科大讯飞websocket服务
        try {
            URI url = new URI(BASE_URL + getHandShakeParams(APPID, SECRET_KEY));
            DraftWithOrigin draft = new DraftWithOrigin(ORIGIN);
            connectClose = new CountDownLatch(1);
            CountDownLatch handshakeSuccess = new CountDownLatch(1);
            client = this.new MyWebSocketClient(url, draft, handshakeSuccess, connectClose);
            client.connect();
        }catch (Exception e){
            e.printStackTrace();
            log.error("==============>连接科大讯飞服务出错！");
        }
    }

    /**
     * 异步执行关闭client任务
     */
    public void stop(){
        long beginStop =System.currentTimeMillis()/1000L;
        while (isSuspend){
            long now = System.currentTimeMillis()/1000L;
            if (now - beginStop >= 15){
                client.close();
                isSuspend = false;
                Iterator<String> iterator = remoteConnectIflyClientInfoMap.keySet().iterator();
                while (iterator.hasNext()){
                    String id = iterator.next();
                    if (Objects.equals(userId,id)){
                        iterator.remove();
                    }
                }
                break;
            }
            try {
            //不断发送空数据
            byte[] bytes = new byte[CHUNCKED_SIZE];
            ByteBuffer byteBuffer = ByteBufferUtils.getEmptyByteBuffer();
            byteBuffer.get(bytes);
            client.send(bytes);
            Thread.sleep(40);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (client.getReadyState()!= WebSocket.READYSTATE.OPEN){
            log.info("==========>异步任务成功关闭连接！");
        }else {
            log.info("==========>异步任务结束！");
        }
    }

    /**
     * 关闭触发方法
     * @param userId
     */
    @OnClose
    public void onClose(@PathParam("userId") String userId) {

        if (remoteConnectIflyClientInfoMap.containsKey(userId)){
            remoteConnectIflyClientInfoMap.remove(userId);
        }
        if (localConnectClientInfoMap.containsKey(userId)){
            localConnectClientInfoMap.remove(userId);
        }

        log.info("==============>用户【{}】的Websocket连接已关闭！",userId);
    }

    /**
     * 连接错误触发方法
     * @param error
     */
    @OnError
    public void onError(Throwable error){
        log.error("==============>Websocket连接服务异常关闭！");
    }

    /**
     * 发送消息触发方法
     * @param message
     */
    @OnMessage
    public void onMessage(ByteBuffer message){
        try {
//            FileInputStream in =new FileInputStream(new File("src/main/resources/voice/pp.pcm"));
////            //当文件没有结束时，每次读取一个字节显示
//            byte[] data=new byte[in.available()];
//            in.read(data);
//            in.close();
//            ByteBuffer message = ByteBuffer.wrap(data);
            //业务方法
            int capacity = message.capacity();
            if (capacity < CHUNCKED_SIZE){
                byte[] bytes = new byte[capacity];
                message.get(bytes);
                client.send(bytes);
                Thread.sleep(40);
            }else {
                while (message.remaining() > CHUNCKED_SIZE){
                    byte[] bytes = new byte[CHUNCKED_SIZE];
                    message.get(bytes);
                    client.send(bytes);
                    Thread.sleep(40);
                }
                byte[] bytes = new byte[message.remaining()];
                message.get(bytes);
                Thread.sleep(40);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    /**
     * 特定字符串可以用来暂停,重新开始
     * @param message
     */
    @OnMessage
    public void onMessage(String message,@PathParam("userId") String userId){

        if (Objects.equals("{\"stop\": true}",message)){
            log.info("==========>触发暂停了");
            isSuspend = true;
            CompletableFuture.runAsync(() -> {
                stop();
            });
        }else if (Objects.equals("{\"continue\": true}",message)){
            log.info("==========>继续录音了");
            //1.判断client是否已经断开
            if (client.getReadyState() != WebSocket.READYSTATE.OPEN){
                try {
                    URI url = new URI(BASE_URL + getHandShakeParams(APPID, SECRET_KEY));
                    DraftWithOrigin draft = new DraftWithOrigin(ORIGIN);
                    CountDownLatch handshakeSuccess = new CountDownLatch(1);
                    connectClose = new CountDownLatch(1);
                    client = this.new MyWebSocketClient(url, draft, handshakeSuccess, connectClose);
                    client.connect();
                }catch (Exception e){
                    e.printStackTrace();
                    log.error("==============>连接科大讯飞服务出错！");
                }
            }else {
                //2.短暂暂停，直接修改isSuppend状态
                isSuspend = false;
            }
        }else if (Objects.equals("{\"close\": begin}",message)){
            closeConnectIflyServer();
            closeWebConnect();
        }
        return;
    }

    /**
     * 保存连接信息到map中
     */
    public void saveInfoInMap(){
        //2.保存连接记录到记录连接总数map中
        if (!localConnectClientInfoMap.containsKey(userId)){
            localConnectClientInfoMap.put(userId,session);
        }
        remoteConnectIflyClientInfoMap.put(userId,session);

        log.info("==============>当前连接Websocket服务的客户端总数为【{}】台，连接用户人数为【{}】人！",localConnectClientInfoMap.size(),remoteConnectIflyClientInfoMap.size());
    }

    /**
     * 关闭本服务和前端的链接
     * @throws Exception
     */
    public void closeConnect(){
        onClose(userId);
    }

    /**
     * 返回转写结果内容
     * @param result
     * @return
     */
    public void sendClientResult(String result){
        try {
            Session session = localConnectClientInfoMap.get(userId);
            if (session != null){
                session.getBasicRemote().sendText(result);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error("==========>返回转写结果内容出错！");
        }

    }

    // 生成握手参数
    public String getHandShakeParams(String appId, String secretKey) {
        String ts = System.currentTimeMillis()/1000 + "";
        String signa = "";
        try {
            signa = EncryptUtil.HmacSHA1Encrypt(EncryptUtil.MD5(appId + ts), secretKey);
            return "?appid=" + appId + "&ts=" + ts + "&signa=" + URLEncoder.encode(signa, "UTF-8");
        } catch (Exception e) {
            log.error("=============>对接科大讯飞接口时生成握手参数失败！");
            e.printStackTrace();
        }

        return "";
    }

    public class MyWebSocketClient extends WebSocketClient {

        private CountDownLatch handshakeSuccess;
        private CountDownLatch connectClose;

        public MyWebSocketClient(URI serverUri, Draft protocolDraft, CountDownLatch handshakeSuccess, CountDownLatch connectClose) {
            super(serverUri, protocolDraft);
            this.handshakeSuccess = handshakeSuccess;
            this.connectClose = connectClose;
            if(serverUri.toString().contains("wss")){
                trustAllHosts(this);
            }
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            System.out.println(getCurrentTimeStr() + "\t连接建立成功！");
        }

        @Override
        public void onMessage(String msg) {
            JSONObject msgObj = JSON.parseObject(msg);
            String action = msgObj.getString("action");
            if (Objects.equals("started", action)) {
                // 握手成功
                log.info("===========>握手成功！sid：{}",msgObj.getString("sid"));
                saveInfoInMap();
                handshakeSuccess.countDown();
                try {
                    session.getBasicRemote().sendText("{\"connect\": true}");
                }catch (Exception e){
                    e.printStackTrace();
                }
            } else if (Objects.equals("result", action)) {
                // 转写结果
//                System.out.println(getCurrentTimeStr() + "\tresult: " + getContent(msgObj.getString("data")));
                //为空，则不返回
                String message = getContent(msgObj.getString("data"));
                if (!Objects.equals("",message)){
                    log.info("打印的消息："+ message);
                    sendClientResult(message);
                }
            } else if (Objects.equals("error", action)) {
                // 连接发生错误
                log.error("==============>科大讯飞服务链接关闭！");
                try {
                    closeConnectIflyServer();
                    session.getBasicRemote().sendText("{\"close\": end}");
                    closeConnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onError(Exception e) {
            System.out.println(getCurrentTimeStr() + "\t连接发生错误：" + e.getMessage() + ", " + new Date());
            e.printStackTrace();
            System.exit(0);
        }

        @Override
        public void onClose(int arg0, String arg1, boolean arg2) {
            System.out.println(getCurrentTimeStr() + "\t链接关闭");
            connectClose.countDown();
            closeWebConnect();
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                System.out.println(getCurrentTimeStr() + "\t服务端返回：" + new String(bytes.array(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        public void trustAllHosts(VoiceTranscriptionService.MyWebSocketClient appClient) {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }

                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    // TODO Auto-generated method stub

                }
            }};

            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                appClient.setSocket(sc.getSocketFactory().createSocket());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 把转写结果解析为句子
    public static String getContent(String message) {
        System.out.println("返回的记过：" + message);
        StringBuffer resultBuilder = new StringBuffer();
        //构造对象
        JSONObject resultObject = new JSONObject();
        String talkPeople = "0";
        try {
            JSONObject messageObj = JSON.parseObject(message);
            JSONObject cn = messageObj.getJSONObject("cn");
            JSONObject st = cn.getJSONObject("st");
            if (Objects.equals("0",st.getString("type"))){

                JSONArray rtArr = st.getJSONArray("rt");
                for (int i = 0; i < rtArr.size(); i++) {
                    JSONObject rtArrObj = rtArr.getJSONObject(i);
                    JSONArray wsArr = rtArrObj.getJSONArray("ws");
                    for (int j = 0; j < wsArr.size(); j++) {
                        JSONObject wsArrObj = wsArr.getJSONObject(j);
                        JSONArray cwArr = wsArrObj.getJSONArray("cw");
                        for (int k = 0; k < cwArr.size(); k++) {
                            JSONObject cwArrObj = cwArr.getJSONObject(k);
                            //获取说话人
                            talkPeople = cwArrObj.getString("rl");
                            String wStr = cwArrObj.getString("w");
                            resultBuilder.append(wStr);
                        }
                    }
                }
                resultObject.put("spokesMan",String.valueOf(Integer.parseInt(talkPeople) + 1));
                resultObject.put("spokesTime",getCurrentTimeStr());
                resultObject.put("spokesContent",resultBuilder.toString());
            }
        } catch (Exception e) {
            return message;
        }
        if (resultObject.size() > 0 && !Objects.equals("",resultObject.getString("spokesContent"))){
            return resultObject.toJSONString();
        }
        return "";
    }

    public static String getCurrentTimeStr() {
        return sdf.format(new Date());
    }
}
