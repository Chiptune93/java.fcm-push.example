import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import net.sf.json.JSONObject;
public class PushThread extends Thread {
	// 단건 푸시 정보 ( CUSTOM )
	private MyMap pushInfo;
	
	// 푸시 서비스 ( CUSTOM )
	private PushSendService pushSendService;
	
	// 파라미터들이 담겨있는 맵 변수 ( CUSTOM )
	private MyMap paramMap;
	
	private final String sendUrl = "https://fcm.googleapis.com/fcm/send";
	
	private final String sendKey = "{fcmSendKey}";
	public PushThread (MyMap pushInfo, MyMap paramMap) {
		this.pushInfo = pushInfo;
		this.paramMap = paramMap;
		this.pushSendService = Beans.get(PushSendService.class);
	}
	
	public void run() {
		// User OS 가져오기
		String platformType = pushInfo.getString("platformType");
		/* 아이폰/안드로이드 분기 */
		if (platformType.equals("i")) {
			IOS_fcm(pushInfo);
		} else if (platformType.equals("a")) {
			Andorid(pushInfo);
		}
		/* 푸시 상태 파악 */
		MyMap pushState = pushSendService.findPushStatus(paramMap);
		int waiting = pushState.getInt("waiting"); // 대기중 건수
		int total = pushState.getInt("ing"); // 전송중인 건수
		// 대기중인 메세지가 있고, 전체 건수가 10건 미만인 경우 재귀호출.
		if (waiting > 0 && total < 10) {
			MyMap oldWatingMsg = pushSendService.getOldestWatingMsg(paramMap);
			this.pushInfo = oldWatingMsg;
			run();
		}
	}
	
	public void Andorid(MyMap pushInfo) {
		try {
			URL url = new URL(sendUrl);
			HttpURLConnection urlConn = (HttpURLConnection)url.openConnection();
			// 헤더 설정
			/* https 통신을 위해, 프로토콜 세팅 ( handshake exception 방지 ) */
			System.setProperty("https.protocols", "TLSv1.2");
			urlConn.setRequestProperty("Content-Type", "application/json");
			urlConn.setRequestProperty("Authorization", "key=" + sendKey);
			urlConn.setDoOutput(true);
			/* 메세지 세팅 */
			JSONObject msg = new JSONObject();
			JSONObject data = new JSONObject();
			msg.put("to", pushInfo.getString("deviceToken")); // 단건 발송일때, 디바이스 토큰
			msg.put("priority", "high"); // 딥슬립 대응
			msg.put("direct_boot_ok", true); // direct_boot 옵션 추가
			data.put("title", pushInfo.getString("title"));
			data.put("message", pushInfo.getString("cont"));
			msg.put("data", data);
			/* 메세지 세팅 */
			urlConn.setRequestMethod("POST");
			urlConn.connect();
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(urlConn.getOutputStream(), "UTF-8"));
			bw.write(msg.toString());
			bw.flush();
			String result = "";
			if (urlConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				BufferedReader br = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
				String temp = "";
				while ((temp = br.readLine()) != null) {
					result += temp;
				}
				br.close();
			} else {}
			JSONObject jsonResult = null;
			if (!result.isEmpty()) {
				jsonResult = JSONObject.fromObject(result);
			} else {
				jsonResult = new JSONObject();
			}
			urlConn.disconnect();
			/* 로그 기록용 세팅 */
			Calendar cal = Calendar.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일 HH시 mm분 ss초");
			String loggerMsg = sdf.format(cal.getTime()) + " // 안드로이드 발송 / deviceToken : " + pushInfo.getString("deviceToken") + " / result : " + jsonResult;
			System.out.println("-----------------------------------------------------------");
			System.out.println(loggerMsg);
			System.out.println("-----------------------------------------------------------");
			MyMap logMap = new MyMap();
			logMap.putAll(pushInfo);
			String s = jsonResult.getString("success");
			if (s.equals("1")) {
				logMap.put("status", "성공");
			} else {
				logMap.put("status", "실패");
			}
			logMap.put("cont", loggerMsg);
			pushSendService.insertPushLog(logMap);
			/* 로그 기록용 세팅 */
		} catch (Exception e) {
			e.printStackTrace();
		}
		finish(pushInfo);
	}
	
	// 아이폰 FCM 발송
	public void IOS_fcm(MyMap pushInfo) {
		MyMap paramMap = new MyMap();
		try {
			URL url = new URL(sendUrl);
			HttpURLConnection urlConn = (HttpURLConnection)url.openConnection();
			// 헤더 설정
			/* https 통신을 위해, 프로토콜 세팅 ( handshake exception 방지 ) */
			System.setProperty("https.protocols", "TLSv1.2");
			urlConn.setRequestProperty("Content-Type", "application/json");
			urlConn.setRequestProperty("Authorization", "key=" + sendKey);
			urlConn.setDoOutput(true);
			/* 메세지 세팅 */
			JSONObject msg = new JSONObject();
			JSONObject notification = new JSONObject();
			JSONObject data = new JSONObject();
			msg.put("to", pushInfo.getString("deviceToken")); // 단건 발송일때, 디바이스 토큰
			msg.put("priority", "high"); // 아이폰 대응 값
			msg.put("content_available", true); // 아이폰 대응 값
			msg.put("direct_boot_ok", true); // direct_boot 옵션 추가
			notification.put("title", pushInfo.getString("title"));
			notification.put("body", pushInfo.getString("cont"));
			notification.put("beadge", 1); // 아이폰만 해당
			notification.put("sound", "default"); // 소리 재생(생략시 쥐도 새도 모르게 푸시가 옴)
			msg.put("notification", notification);
			msg.put("data", data);
			/* 메세지 세팅 */
			urlConn.setRequestMethod("POST");
			urlConn.connect();
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(urlConn.getOutputStream(), "UTF-8"));
			bw.write(msg.toString());
			bw.flush();
			String result = "";
			if (urlConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				BufferedReader br = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
				String temp = "";
				while ((temp = br.readLine()) != null) {
					result += temp;
				}
				br.close();
			} else {}
			JSONObject jsonResult = null;
			if (!result.isEmpty()) {
				jsonResult = JSONObject.fromObject(result);
			} else {
				jsonResult = new JSONObject();
			}
			urlConn.disconnect();
			/* 로그 기록용 세팅 */
			Calendar cal = Calendar.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일 HH시 mm분 ss초");
			String loggerMsg = sdf.format(cal.getTime()) + " // 아이폰 FCM 발송 / deviceToken : " + pushInfo.getString("sapqToken") + " / result : " + jsonResult;
			System.out.println("-----------------------------------------------------------");
			System.out.println(loggerMsg);
			System.out.println("-----------------------------------------------------------");
			MyMap logMap = new MyMap();
			logMap.putAll(pushInfo);
			String s = jsonResult.getString("success");
			if (s.equals("1")) {
				logMap.put("status", "성공");
			} else {
				logMap.put("status", "실패");
			}
			logMap.put("cont", loggerMsg);
			pushSendService.insertPushLog(logMap);
			/* 로그 기록용 세팅 */
		} catch (Exception e) {
			e.printStackTrace();
		}
		finish(pushInfo);
	}
	
	// 푸시 전송 후 작업
	public void finish(MyMap pushInfo) {
    // 메세지 아예 삭제 또는 푸시 상태만 완료로 변경
		// pushSendService.deleteSendData(pushInfo); 푸시 내용 삭제
		pushInfo.put("status", "COMPLETE");
		pushSendService.updateStatus(pushInfo); // 푸시 COMPLETE 로 변경.
	}
