package jp.libroworks;


public interface GameClientListener {
	// コマンド受信時に呼び出されるメソッド
	// commandが0の場合はサーバからのエラーメッセージ
	public void receive(int command, int startpt, int endpt, String body);
	// 受信エラーが発生したときに呼び出されるメソッド
	// 必要なければ何もしなくてもいい
	public void receiveError();
	// 受信用タイマーを他のことに使いたいとき
	public void receiveTimerRun();
}
