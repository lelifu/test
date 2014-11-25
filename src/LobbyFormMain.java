import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import jp.libroworks.GameClientListener;
import jp.libroworks.GameCommunicator;
import jp.libroworks.LobbyForm;

public class LobbyFormMain extends JFrame implements GameClientListener, ActionListener {
	private static final long serialVersionUID = 1L;

	// エントリポイント
	public static void main(String[] args) {
		new LobbyFormMain();
	}

	// 通信関連フィールド
	private static final String GAMEIDPD = "12345678-1234-1234-1234-123456789ABC|password";
	private static final String SERVERHOSTNAME = "aira3-pc";
	private static final int WAITPORT = 15008;
	GameCommunicator communicator = null;
	private static final int UCOM_SENDMESSAGE = 1000;

	// テキストボックスとボタン
	JTextField tf_message = new JTextField(20);
	JTextArea ta_result = new JTextArea();
	JButton btn_send = new JButton("送信");

	// ロビーフォーム
	LobbyForm lobbyform = null;

	// コンストラクタ
	public LobbyFormMain() {
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		// クローズ時処理：サーバから切断
		this.addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent arg0) {
				// disconnectメソッド呼び出し
				if (communicator != null)
					communicator.disconnect();
			}
		});

		// ウィンドウ初期化処理
		this.setTitle("チャット");
		Container cPane = this.getContentPane();
		// 入力エリア
		JPanel panel1 = new JPanel();
		panel1.setLayout(new BoxLayout(panel1, BoxLayout.X_AXIS));
		panel1.add(this.tf_message);
		panel1.add(this.btn_send);
		this.btn_send.setEnabled(false);
		cPane.add(panel1, BorderLayout.NORTH);
		// メッセージエリア
		this.ta_result.setRows(8);
		this.ta_result.setEditable(false);
		JScrollPane scpane = new JScrollPane(this.ta_result);
		cPane.add(scpane, BorderLayout.CENTER);
		// 送信ボタンにアクション割り当て
		this.btn_send.addActionListener(this);
		// デフォルトボタン（Enterキーで押せるボタン）に設定
		this.getRootPane().setDefaultButton(this.btn_send);

		this.pack(); // ウィンドウサイズ自動調整
		this.setVisible(true);
		this.setLocationRelativeTo(null); // デスクトップ中央に配置

		// サーバに接続してゲームにログイン
		this.communicator = new GameCommunicator();
		this.communicator.connectServer(SERVERHOSTNAME, WAITPORT, GAMEIDPD);

		// ログイン確認
		if (this.communicator.isLogin() == true) {
			// ユーザー名の設定ダイアログ表示
			String username = null;
			while (username == null) {
				username = JOptionPane.showInputDialog(this,
						"自分のニックネームを付けてください");
			}

			// 自分のユーザー名の登録
			this.communicator.sendToServer(GameCommunicator.COM_SETUSERNAME, 0,
					0, username);
			this.setTitle("チャット: " + username);
			// ロビーフォームダイアログの表示
			this.lobbyform = new LobbyForm(this, this.communicator, GAMEIDPD);
			this.lobbyform.setUsername(username);
			this.lobbyform.setMultiplay(false);
			this.lobbyform.showRobby();
			// モーダルダイアログにすると受信タイマーが停止してしまうため、
			// 疑似モーダルにするためのループ
			while (this.lobbyform.isVisible()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			System.out.println("モーダル解除");
			// 対戦準備が整っていたら送信ボタンを有効にする
			if (this.lobbyform.getSucceed() == true) {
				this.communicator.setGameClientListener(this);
				this.btn_send.setEnabled(true);
			}
		} else {
			System.out.println("何らかの理由でログインに失敗");
			JOptionPane.showMessageDialog(null, "サーバ接続またはログインに失敗しました。\n"
					+ "ID・パスワード・ホストネーム・ポートのいずれかが間違っているか、\n"
					+ "サーバが落ちている可能性があります。", "接続エラー",
					JOptionPane.WARNING_MESSAGE);

		}

	}

	// メッセージ受信時に呼び出されるメソッド
	public void receive(int command, int startpt, int endpt, String body) {
		if (command == UCOM_SENDMESSAGE) {
			// 相手からのメッセージを受信
			// 受信したメッセージを表示
			this.ta_result.append(this.lobbyform.getOpponentName() + ": "
					+ body + "\n");
			// スクロール位置調整
			this.ta_result.setCaretPosition(ta_result.getText().length());
		} else if (command == 0) {
			// エラー発生
			this.ta_result.append("送信したメッセージは相手に受信されませんでした\n");
			// スクロール位置調整
			this.ta_result.setCaretPosition(ta_result.getText().length());

		}
	}

	// 受信エラーが発生したときに呼び出されるメソッド
	// 必要なければ何もしなくてもいい
	public void receiveError() {
	}

	// Communicatorの受信用タイマーを利用した定期イベント呼び出し
	public void receiveTimerRun() {
		// TODO Auto-generated method stub

	}

	public void actionPerformed(ActionEvent e) {
		// 送信ボタンが押された
		if (e.getSource() == this.btn_send) {
			// 文字数を確認
			String sendmessage = this.tf_message.getText();
			if (sendmessage.length() > 0) {
				// メッセージ送信
				this.communicator.sendToServer(UCOM_SENDMESSAGE,
						this.lobbyform.getUserID(),
						this.lobbyform.getOpponentID(), sendmessage);
				// 送信したメッセージを表示
				this.ta_result.append(this.lobbyform.getUsername() + ": "
						+ sendmessage + "\n");
				this.tf_message.setText("");
				// スクロール位置調整
				this.ta_result.setCaretPosition(ta_result.getText().length());
			}
		}
	}

}
