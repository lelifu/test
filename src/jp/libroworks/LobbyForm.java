package jp.libroworks;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class LobbyForm extends JDialog implements GameClientListener,
		ActionListener, ListSelectionListener {

	private static final long serialVersionUID = 1L;

	private int userID = -1; // ユーザーID
	private String username; // ユーザー名
	private int numrooms = 0; // 部屋数
	private int roomID = -1; // 入室中のルームID
	// ユーザーIDとユーザー名の対応表
	private HashMap<String, Integer> usermap = new HashMap<String, Integer>();
	private boolean multiplay = false;
	private int oppID = -1; // 対戦相手のID
	private String oppname; // 対戦相手の名前
	private boolean oppstatus = false; // 対戦相手の状態

	//対戦開始に成功したかどうかを示すフラグ
	private boolean succeed = false;

	// コントロール類
	private JButton btn_joinRoom = new JButton("入室");
	private JButton btn_offer = new JButton("対戦申し込み");
	private GameCommunicator communicator = null;
	private DefaultListModel model_users = new DefaultListModel();
	private JList lst_users = new JList(this.model_users);
	private JComboBox cmb_rooms = new JComboBox();
	private JTextArea ta_userinfo = new JTextArea(0, 10);

	// コンストラクタ
	// 引数：親フレーム、サーバとの通信チャンネル、ゲームログイン用IDとPassword
	public LobbyForm(JFrame mainframe, GameCommunicator gcom,
			String gameIDPD) {
		super(mainframe, "ゲームロビー", false);
		this.communicator = gcom;

		Container cpane = this.getContentPane();
		JScrollPane jspane1 = new JScrollPane(this.lst_users);
		this.lst_users.setForeground(Color.WHITE);
		this.lst_users.setBackground(Color.BLACK);
		jspane1.setPreferredSize(new Dimension(360, 160));
		cpane.add(jspane1, BorderLayout.CENTER);
		JPanel panel2 = new JPanel();
		panel2.setLayout(new BoxLayout(panel2, BoxLayout.X_AXIS));
		panel2.add(this.cmb_rooms);
		panel2.add(this.btn_joinRoom);
		panel2.add(this.btn_offer);
		cpane.add(panel2, BorderLayout.SOUTH);
		this.ta_userinfo.setEditable(false);
		this.ta_userinfo.setBackground(Color.LIGHT_GRAY);
		cpane.add(this.ta_userinfo, BorderLayout.EAST);
		// cpane.add(this.lbl_userstatus, BorderLayout.EAST); //TODO

		this.pack();
		this.setLocationRelativeTo(null);

		// アクション設定
		this.btn_joinRoom.addActionListener(this);
		this.btn_offer.addActionListener(this);
		this.cmb_rooms.addActionListener(this);
		this.lst_users.addListSelectionListener(this);

		// ダイアログボックスが非表示になったときリスナーを削除
		this.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentHidden(ComponentEvent arg0) {
				communicator.removeGameTimerListener();
				communicator.removeGameClientListener();
				btn_offer.setEnabled(true);
			}
		});
	}

	// ゲッターセッター群
	public void setUsername(String username) {
		this.username = username;
		this.setTitle("ゲームロビー：" + this.username);
	}

	public String getUsername() {
		return username;
	}

	public int getRoomID() {
		return roomID;
	}

	public int getUserID() {
		return this.userID;
	}

	// マルチ対戦モードでは相手が対戦中状態でもログインできる
	// デフォルトはfalse（一対一）
	public void setMultiplay(boolean multiplay) {
		this.multiplay = multiplay;
	}

	public HashMap<String, Integer> getUserMap() {
		return this.usermap;
	}

	public int getOpponentID() {
		return this.oppID;
	}

	public String getOpponentName() {
		return this.oppname;
	}
	//対戦準備が整った場合はtrueを返す
	public boolean getSucceed(){
		return this.succeed;
	}

	// ロビーフォームを表示する
	public void showRobby() {
		this.oppID = -1;
		this.oppname = "";
		this.oppstatus = false;
		this.succeed = false;

		this.pack();
		this.setLocationRelativeTo(null);
		this.setVisible(true);
		
		this.communicator.setGameClientListener(this);
		this.communicator.setGameTimerListener(this);
		// 自分のユーザーIDを問い合わせ
		this.communicator.sendToServer(GameCommunicator.COM_GETUSERID, 0, 0,
				null);
		// サーバに部屋数を問い合わせ
		this.communicator
				.sendToServer(GameCommunicator.COM_ROOMNUM, 0, 0, null);
		// 自分を非対戦中にする
		this.communicator.sendToServer(GameCommunicator.COM_SETSELFSTAT,
				this.userID, 0, Code64.encodeBoolean(false).toString());
	}

	// データ受信時に呼び出されるメソッド
	public void receive(int command, int startpt, int endpt, String body) {
		switch (command) {
		case -GameCommunicator.COM_GETUSERID:
			// 自分のユーザーIDの問い合わせ返信
			if (body != null) {
				this.userID = Code64.decodeShort(body);
			}
			break;
		case -GameCommunicator.COM_ROOMNUM:
			// ルーム数の問い合わせ返信
			if (body != null) {
				// ルーム数を取得
				this.numrooms = Code64.decodeShort(body);
				// ルームに対応するListを追加
				for (int i = 0; i < this.numrooms; i++) {
					this.cmb_rooms.addItem("Room" + i);
				}
				// ルームメンバーの確認を開始
				this.communicator.sendToServer(GameCommunicator.COM_MEMBERNUM,
						0, 0, "000");
			}
			break;
		case -GameCommunicator.COM_JOINROOM:
			// 入室の問い合わせ返信
			if (body != null) {
				// 入室したルームIDを取得
				this.roomID = Code64.decodeShort(body);
				// ルームメンバーの確認を開始
				this.communicator.sendToServer(GameCommunicator.COM_MEMBERNUM,
						0, 0, body);
				// 次の更新がしばらく行われないように工夫
				this.reloadcounter = 1;
			}
			break;
		case -GameCommunicator.COM_MEMBERNUM:
			// 指定したルームのメンバー数問い合わせの返信
			// 引き続きルーム内メンバーの紹介が開始される
			if (body != null) {
				int membernum = Code64.decodeShort(body);
				this.model_users.clear();
				this.usermap.clear();
				if (membernum > 0) {
					for (int i = 0; i < membernum; i++) {
						this.model_users.addElement("member" + i);
					}
					// 選択ルームの0番目のユーザーの名前とIDを照会
					this.communicator.sendToServer(
							GameCommunicator.COM_REFMEMBER, this.userID, 0,
							Code64.encodeShort(
									(short) this.cmb_rooms.getSelectedIndex())
									.append(Code64.encodeShort((short) 0))
									.toString());
				}
			}
			break;
		case -GameCommunicator.COM_REFMEMBER:
			// メンバー照会の返信
			if (body != null) {
				// bodyのデータを取り出し
				int refroomID = Code64.decodeShort(body.substring(0, 3));
				int refuseridx = Code64.decodeShort(body.substring(3, 6));
				int refuserID = Code64.decodeShort(body.substring(6, 9));
				String refusername = body.substring(9);
				// 選択した部屋が変更されていないか確認
				if (refroomID == this.cmb_rooms.getSelectedIndex()) {
					// 念のためリストの登録数がユーザーインデックスより上であることを確認
					if (refuseridx < this.model_users.getSize()) {
						// リストのユーザー名を変更
						this.model_users.set(refuseridx, refusername);
						// ユーザー名とユーザーIDの関係を登録
						this.usermap.put(refusername, refuserID);
						// 最後のユーザーでなければ次の照会を実行
						if (refuseridx < this.model_users.getSize() - 1) {
							this.communicator
									.sendToServer(
											GameCommunicator.COM_REFMEMBER,
											this.userID,
											0,
											Code64
													.encodeShort(
															(short) this.cmb_rooms
																	.getSelectedIndex())
													.append(
															Code64
																	.encodeShort((short) (refuseridx + 1)))
													.toString());
						}
					}
				}
			}
			break;
		case -GameCommunicator.COM_REFUSERSTAT:
			// メンバーの情報紹介の返信
			if (body != null) {
				this.oppstatus = Code64.decodeBoolean(body.substring(3));
				if (this.oppstatus == true) {
					this.ta_userinfo.append("status: プレイ中");
					this.ta_userinfo.setBackground(Color.LIGHT_GRAY);
				} else {
					this.ta_userinfo.append("status: 待機");
					this.ta_userinfo.setBackground(new Color(200,255,200));
				}
			}
			break;
		case GameCommunicator.COM_OFFERPLAY:
			// 対戦を申し込まれた
			int result = JOptionPane.showConfirmDialog(this, body
					+ "が対戦を申し込んできました", "ゲームロビー", JOptionPane.YES_NO_OPTION);
			boolean accept = true;
			if (result == JOptionPane.NO_OPTION)
				accept = false;
			// 返事を送る（trueなら対戦了解、falseなら断り
			this.communicator.sendToServer(-GameCommunicator.COM_OFFERPLAY,
					endpt, startpt, Code64.encodeBoolean(accept).toString());
			// 申し込みを受け入れた場合はログインフォームを閉じる
			if (accept) {
				this.oppname = body;
				this.oppID = startpt;
				this.setSucceed();
			}
			break;
		case -GameCommunicator.COM_OFFERPLAY:
			// 対戦申し込みの返事が来た
			if (body != null) {
				boolean reply = Code64.decodeBoolean(body);
				if (reply == true) {
					// 対戦を受け入れられた
					this.oppID = startpt;
					this.setSucceed();
				} else {
					// 対戦を断られた
					// 対戦ボタンを有効に戻す
					this.btn_offer.setEnabled(true);
					JOptionPane.showMessageDialog(this, "対戦申し込みは受け入れられませんでした");
				}
			}
			break;
		}
	}

	//対戦準備完了
	private void setSucceed(){
		this.succeed = true;
		// 自分を対戦中にする
		this.communicator.sendToServer(
				GameCommunicator.COM_SETSELFSTAT, this.userID, 0,
				Code64.encodeBoolean(true).toString());
		// ログインフォームを閉じる
		this.setVisible(false);
	}

	public void receiveError() {
	}

	// ボタンが操作されると呼び出されるメソッド
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == this.btn_joinRoom) {
			// 入室ボタンが押された
			// コンボボックスの選択インデックスを取得
			int roomID = this.cmb_rooms.getSelectedIndex();
			this.communicator.sendToServer(GameCommunicator.COM_JOINROOM,
					this.userID, 0, Code64.encodeShort((short) roomID)
							.toString());
		} else if (e.getSource() == this.cmb_rooms) {
			// ルームメンバーの確認を開始
			this.communicator.sendToServer(GameCommunicator.COM_MEMBERNUM, 0,
					0, Code64.encodeShort(
							(short) this.cmb_rooms.getSelectedIndex())
							.toString());
			// 次の更新がしばらく行われないように工夫
			this.reloadcounter = 1;
		} else if (e.getSource() == this.btn_offer) {
			// 対戦ボタンが押された
			// ユーザーが1人以上いるかどうか念のため確認
			if (this.model_users.size() < 1) {
				java.awt.Toolkit.getDefaultToolkit().beep();
				return;
			}
			// まだ入室していない（roomIDが-1）または別ルームならエラー
			if (this.roomID != this.cmb_rooms.getSelectedIndex()) {
				java.awt.Toolkit.getDefaultToolkit().beep();
				JOptionPane.showMessageDialog(this, "まず入室してください");
				return;
			}
			// 対戦申し込んでいる相手が自分ならエラー
			if (this.userID == this.oppID) {
				java.awt.Toolkit.getDefaultToolkit().beep();
				return;
			}
			if (this.multiplay == true) {
				// マルチプレイモードのときは状態関係なく対戦開始
				this.setSucceed();
			} else {
				// 一対一対戦モード
				if (this.oppstatus == true) {
					// 相手が対戦中の場合もエラー音
					java.awt.Toolkit.getDefaultToolkit().beep();
				} else {
					// 対戦申し込みを送る
					this.communicator.sendToServer(
							GameCommunicator.COM_OFFERPLAY, this.userID,
							this.oppID, this.username);
					// 対戦ボタンを無効にする
					this.btn_offer.setEnabled(false);
				}
			}
		}
	}

	private int reloadcounter = 0;

	// データ受信確認のついでに呼ばれるタイマー
	public void receiveTimerRun() {
		// タイマーを使って600回に1回（30秒に1回）現在のルームのメンバー情報を更新
		reloadcounter++;
		reloadcounter = reloadcounter % 600;
		if (reloadcounter == 0) {
			// ルームメンバーの確認を開始
			this.communicator.sendToServer(GameCommunicator.COM_MEMBERNUM, 0,
					0, Code64.encodeShort(
							(short) this.cmb_rooms.getSelectedIndex())
							.toString());
		}
	}

	// ユーザーリスト選択時に呼び出されるメソッド
	public void valueChanged(ListSelectionEvent e) {
		if (e.getSource() == this.lst_users) {
			if (e.getValueIsAdjusting()) {

				// ユーザーが1人以上いるかどうか念のため確認
				if (this.model_users.size() > 0) {
					String refusername = (String) this.lst_users
							.getSelectedValue();
					// 名前と対応するユーザーIDを取得
					Integer refuserID = this.usermap.get(refusername);
					if (refuserID != null) {
						// 情報パネルのテキストを更新
						this.ta_userinfo.setText("name: " + refusername + "\n");
						if (this.userID == refuserID) {
							this.ta_userinfo.append("(YOU)\n");
						}
						this.ta_userinfo.append("ID: " + refuserID + "\n");
						// ルームメンバーの状態確認を開始
						this.communicator.sendToServer(
								GameCommunicator.COM_REFUSERSTAT, 0, 0, Code64
										.encodeShort(refuserID.shortValue())
										.toString());
						this.oppID = refuserID;
						this.oppname = refusername;
					}
				}
			}
		}

	}

}
