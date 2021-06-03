package syworks.base.beans;
import java.util.Calendar;
import javax.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;

public class PushScheduleBean {
	// 푸시 관련 서비스 ( CUSTOM )
	@Autowired
	private PushSendService pushSendService;
	public void send() {
		Calendar today = Calendar.getInstance();
		int year = today.get(Calendar.YEAR);
		int month = today.get(Calendar.MONTH) + 1;
		int day = today.get(Calendar.DATE);
		int sendHh = today.get(Calendar.HOUR);
		int sendMm = today.get(Calendar.MINUTE);
		int amPm = today.get(Calendar.AM_PM);
		String strMonth = month < 10 ? "0" + Integer.toString(month) : Integer.toString(month);
		String strDay = day < 10 ? "0" + Integer.toString(day) : Integer.toString(day);
		String sendDt = Integer.toString(year) + "-" + strMonth + "-" + strDay;
		sendHh = amPm == 1 ? sendHh + 12 : sendHh;
		MyMap paramMap = new MyMap();
		paramMap.put("sendDt", sendDt);
		paramMap.put("sendHh", sendHh);
		paramMap.put("sendMm", sendMm);
		paramMap.put("status", "WAIT");
		send(paramMap);
	}

	private void send(MyMap paramMap) {
		// 현재 전송 및 대기 건수 파악.
		MyMap pushState = pushSendService.findPushStatus(paramMap);
		int waiting = pushState.getInt("waiting"); // 대기중 건수
		int total = pushState.getInt("ing"); // 전송중인 건수
		// 대기중 푸시가 있고, 전체 전송 중인 건수가 10개이하 일 때, 재귀 호출
		if (waiting > 0 && total < 10) {
			MyMap oldestWatingMsg = pushSendService.getOldestWatingMsg(paramMap);
			MyMap input = new MyMap();
			input.put("sapqSeq", oldestWatingMsg.get("sapqSeq"));
			input.put("status", "ING");
			pushSendService.updateStatus(input);
      // Thread Start
			PushThread pushThread = new PushThread(oldestWatingMsg, paramMap);
			pushThread.start();
		}
		pushState = pushSendService.findPushStatus(paramMap);
		waiting = pushState.getInt("waiting");
		total = pushState.getInt("ing");
		if (waiting > 0 && total < 10) {
			send(paramMap);
		}
	}
}
