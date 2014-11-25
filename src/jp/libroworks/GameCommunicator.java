package jp.libroworks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;

public class GameCommunicator {

	// 通信用フィールド
	private static final int BUFMAXSIZE = 1024;
	private SocketChannel channel = null;
	private Charset charset = Charset.forName("UTF-8");
	private ByteBuffer readbuf = ByteBuffer.allocate(BUFMAXSIZE);
	private CharBuffer sendbuf = CharBuffer.allocate(BUFMAXSIZE);

	private GameClientListener gclistener = null;
	private GameClientListener gtlistener = null;
	private Timer timer;
	private boolean gamelogin = false;

	// サーバとの接続
	public void connectServer(String hostname, int waitport, String gameIDPD) {
		// サーバへの接続、チャンネルオープン
		try {
			this.channel = SocketChannel.open(new InetSocketAddress(hostname,
					waitport));
			this.channel.configureBlocking(false);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		// 受信監視用タイマー
		// 呼び出し回数1秒間に20回
		this.timer = new Timer();
		this.timer.schedule(new ReceiveTimerTask(), 0, 50);

		// サーバにログインコマンドを送る
		this.sendToServer(1, 0, 0, gameIDPD);
	}

	// ログイン完了確認
	// 非同期通信なので接続確認ができるまで一定時間待機する
	public boolean isLogin() {
		// 1秒おきに10回確認する
		for (int i = 0; i < 10; i++) {
			if (this.gamelogin == true)
				return true;
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	// サーバと切断する
	public void disconnect() {
		try {
			this.timer.cancel();
			if (this.channel != null) {
				this.channel.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// サーバにメッセージを送信する
	// bodyがない場合はnullを指定
	public void sendToServer(int command, int startpt, int endpt, String body) {
		// コマンドのエンコード
		this.sendbuf.clear();
		this.sendbuf.append((char) 02);
		this.sendbuf.append('1');
		this.sendbuf.append(Code64.encodeShort((short) command));
		this.sendbuf.append(Code64.encodeShort((short) startpt));
		this.sendbuf.append(Code64.encodeShort((short) endpt));
		if (body != null) {
			this.sendbuf.append(Code64.encodeShort((short) body.length()));
			this.sendbuf.append(body);
		} else {
			this.sendbuf.append("000");
		}
		this.sendbuf.append((char) 03);
		this.sendbuf.flip();

		System.out.println("送信: " + this.sendbuf.toString());
		if (channel.isConnected()) {
			try {
				this.channel.write(charset.encode(this.sendbuf));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} else {
			System.out.println("サーバより切断された");
		}
	}

	// このコンポーネントとリスナーを関連付ける
	// 注：すでに追加されているかどうかのチェックは行わない
	// つまり、新しいリスナーが登録されると古いリスナーは呼び出されなくなる
	public void setGameClientListener(GameClientListener listener) {
		this.gclistener = listener;
	}

	public void removeGameClientListener() {
		this.gclistener = null;
	}

	// このコンポーネントが持つタイマーを受信以外のことに使用する
	public void setGameTimerListener(GameClientListener listener) {
		this.gtlistener = listener;
	}

	public void removeGameTimerListener() {
		this.gtlistener = null;
	}

	// 受信処理用タイマータスク
	class ReceiveTimerTask extends TimerTask {

		public void run() {
			if (channel.isOpen()) {
				// データの受信処理
				try {
					readbuf.clear();
					if (channel.read(readbuf) > 0) {
						readbuf.flip();
						CharBuffer cbuf = charset.decode(readbuf);

						// バッファのデータからコマンドを切り出して解読
						// エラー対処、markをlimitの位置に設定しておく
						cbuf.position(cbuf.limit());
						cbuf.mark();
						cbuf.flip();
						while (cbuf.position() < cbuf.limit()) {
							// 開始コード02を検索
							if (cbuf.charAt(0) == (char) 02) {
								cbuf.mark(); // 開始位置02をマーク
							}
							// 終了コード03を検索
							if (cbuf.charAt(0) == (char) 03) {
								int pos = cbuf.position(); // 03のある位置を記録
								cbuf.reset(); // 現在位置をマークに戻す
								// 03が02より後にあることを確認
								if (pos > cbuf.position()) {
									// 現在位置以降をデコード
									this.decode(cbuf.slice());
								} else {
									// 受信エラー：03の前に02が見つからない
									this.recieveError();
								}
								// 現在位置を03のある位置に移動
								cbuf.position(pos);
							}
							cbuf.get();
						}
						// 受信エラーチェック：
						// 正常なコマンドが処理されていればmarkは最後のコマンドの先頭にあるはず
						// つまりmarkがlimit位置にあるなら何も見つからなかった
						cbuf.reset(); // 現在位置をマークに戻す
						if (cbuf.limit() == cbuf.position()) {
							this.recieveError();
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					JOptionPane.showMessageDialog(null,
							"サーバまでのネットワーク経路に障害が発生したか、\n" + "サーバが落ちた可能性があります。\n"
									+ "プログラムを強制終了します。", "致命的なエラー",
							JOptionPane.WARNING_MESSAGE);
					System.exit(-1);
				}
			}
			if (gtlistener != null) {
				gtlistener.receiveTimerRun();
			}
		}

		private void decode(CharBuffer cbuf) {
			System.out.println("受信: " + cbuf.toString());
			// ログイン成功確認
			int command = Code64.decodeShort(cbuf.subSequence(2, 5).toString());
			if (command == -1) {
				gamelogin = true;
			}

			// イベントハンドラ呼び出し
			if (gclistener != null) {
				int startpt = Code64.decodeShort(cbuf.subSequence(5, 8)
						.toString());
				int endpt = Code64.decodeShort(cbuf.subSequence(8, 11)
						.toString());
				int bodylen = Code64.decodeShort(cbuf.subSequence(11, 14)
						.toString());
				if (bodylen > 0) {
					gclistener.receive(command, startpt, endpt, cbuf
							.subSequence(14, 14 + bodylen).toString());
				} else {
					gclistener.receive(command, startpt, endpt, null);
				}
			}
		}

		// 受信エラー発生
		private void recieveError() {
			System.out.println("受信したが解読不能");
			if (gclistener != null) {
				gclistener.receiveError();
			}
		}
	}

	public static final int COM_GAMELOGIN = 1;
	public static final int COM_GETUSERID = 2;
	public static final int COM_SETUSERNAME = 3;
	public static final int COM_ROOMNUM = 4;
	public static final int COM_MEMBERNUM = 5;
	public static final int COM_OPENSEAT = 6;
	public static final int COM_REFMEMBER = 7;
	public static final int COM_REFUSERSTAT = 8;
	public static final int COM_JOINROOM = 9;
	public static final int COM_SETSELFSTAT = 10;
	public static final int COM_LEAVEROOM = 11;
	public static final int COM_LEAVEGAME = 12;
	public static final int COM_OFFERPLAY = 13;

}
