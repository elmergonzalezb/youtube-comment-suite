package mattw.youtube.commensuitefx;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.jsoup.Jsoup;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

class CommentResult extends HBox {
	
	final static Map<String,Image> profileMap = new HashMap<>();
	final static Image BLANK_PROFILE = new Image(CommentResult.class.getResourceAsStream("/mattw/youtube/commentsuite/images/blank_profile.jpg"));
	
	private static CommentResult lastSelected = null;
	
	private final CommentType ct;
	
	private final ImageView img;
	private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a");
	private final Hyperlink author;
	private final Label date;
	private final Label textShort;
	private final Label likes;
	private final Hyperlink reply;
	private final Hyperlink viewtree;
	private final Hyperlink viewfulltext;
	private final MenuItem loadProfile;
	private final MenuItem openInBrowser;
	private final MenuItem copyName;
	private final MenuItem copyText;
	private final MenuItem copyChannelLink;
	private final MenuItem copyVideoLink;
	private final MenuItem copyCommentLink;
	
	private final String parsedText;
	
	private boolean selected;
	
	private void setSelected(boolean select) {
		selected = select;
		if(selected) {
			if(lastSelected != null) lastSelected.setSelected(false);
			setId("itemSelected");
			lastSelected = this;
			Platform.runLater(() -> {
				try {
					CommentSuiteFX.getApp().loadContext(ct.getVideoId());
				} catch (SQLException e) {
					e.printStackTrace();
				}
			});
		} else {
			setId(ct.isReply() ? "commentReply" : "");
		}
	}
	
	private boolean isSelected() {
		return selected;
	}
	
	public CommentResult(CommentType c, boolean showTreeLink) {
		super();
		ct = c;
		setMinHeight(90);
		setPrefHeight(90);
		setMaxHeight(90);
		setMinWidth(200);
		setPrefWidth(750);
		setMaxWidth(950);
		setSelected(false);
		setAlignment(Pos.CENTER_LEFT);
		
		VBox box = new VBox();
		box.setMaxWidth(75);
		box.setPrefWidth(75);
		box.setMinWidth(75);
		box.setAlignment(Pos.CENTER);
		img = new ImageView(BLANK_PROFILE);
		img.setFitHeight(32);
		img.setFitWidth(32);
		Image thumb = DatabaseManager.getChannel(c.getChannelId()).fetchThumb();
		if(thumb != null)
			img.setImage(thumb);
		box.getChildren().addAll(img, c.isReply() ? new Label("Reply") : new Label("Comment"));
		
		author = new Hyperlink(DatabaseManager.getChannel(c.getChannelId()).getTitle());
		author.setOnAction(e -> CommentSuiteFX.openInBrowser("https://www.youtube.com/channel/"+c.getChannelId()));
		if(CommentSuiteFX.getApp().getConfig().isSignedIn(c.getChannelId())) {
			author.setId("commentMine");
		}
		date = new Label(sdf.format(c.getDate()));
		date.setId("commentDate");
		
		likes = new Label(c.getLikes() > 0 ? "+"+c.getLikes() : "");
		likes.setId("commentLikes");
		
		int length = 400;
		parsedText = Jsoup.parse(c.getText().replace("<br />", "\r\n")).text();
		textShort = new Label(parsedText.length() > length ? parsedText.substring(0, length-3)+"..." : parsedText);
		textShort.setWrapText(true);
		
		reply = new Hyperlink("Reply");
		reply.setOnAction(e -> replyToComment());
		
		viewtree = new Hyperlink("View Tree"+(c.getReplies() > 0 ? " ("+c.getReplies()+" replies)" : ""));
		viewtree.setDisable(!showTreeLink);
		viewtree.setOnAction(e -> {
			try {
				CommentSuiteFX.getApp().viewTree(this.ct);
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		});
		
		viewfulltext = new Hyperlink("Show Full Comment");
		viewfulltext.setOnAction(e -> viewFullComment());
		
		HBox hbox = new HBox(10);
		hbox.setAlignment(Pos.CENTER_LEFT);
		hbox.getChildren().add(date); // save can_reply
		if(c.getLikes() > 0) hbox.getChildren().add(likes);
		if(!CommentSuiteFX.getApp().getConfig().accounts.isEmpty() || true) hbox.getChildren().add(reply);
		if(c.getReplies() > 0 || c.isReply()) hbox.getChildren().add(viewtree);
		hbox.getChildren().add(viewfulltext);
		
		VBox text = new VBox();
		text.setAlignment(Pos.CENTER_LEFT);
		text.getChildren().addAll(author, textShort, hbox);
		
		getChildren().addAll(box, text);
		
		ContextMenu context = new ContextMenu();
		
		openInBrowser = new MenuItem("Open in Browser");
		openInBrowser.setOnAction(e -> CommentSuiteFX.openInBrowser(ct.getYoutubeLink()));
		
		loadProfile = new MenuItem("Load Profile Image");
		loadProfile.setOnAction(e -> {
			try {
				loadProfileImage();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		});
		
		copyName = new MenuItem("Copy Username");
		copyName.setOnAction(e -> Clipboards.setClipboard(DatabaseManager.getChannel(ct.getChannelId()).getTitle()));
		
		copyText = new MenuItem("Copy Comment");
		copyText.setOnAction(e -> Clipboards.setClipboard(ct.getText()));
		
		copyChannelLink = new MenuItem("Copy Channel Link");
		copyChannelLink.setOnAction(e -> Clipboards.setClipboard(DatabaseManager.getChannel(ct.getChannelId()).getYoutubeLink()));
		
		copyVideoLink = new MenuItem("Copy Video Link");
		copyVideoLink.setOnAction(e -> Clipboards.setClipboard("https://youtu.be/"+ct.getVideoId()));
		
		copyCommentLink = new MenuItem("Copy Comment Link");
		copyCommentLink.setOnAction(e -> Clipboards.setClipboard(ct.getYoutubeLink()));
		
		context.getItems().addAll(openInBrowser, loadProfile, new SeparatorMenuItem(), copyName, copyText, copyChannelLink, copyVideoLink, copyCommentLink);
		
		setOnMouseClicked(e -> {
			if(e.isPopupTrigger()) {
				if(!context.isShowing()) {
					context.show(this, e.getScreenX(), e.getScreenY());
				}
			} else {
				if(context.isShowing()) {
					context.hide();
				}
				setSelected(!isSelected());
			}
		});
	}
	
	private void replyToComment() {
		ComboBox<Account> account = new ComboBox<>();
		account.getItems().addAll(CommentSuiteFX.getApp().getConfig().accounts);
		if(!account.getItems().isEmpty()) {
			account.getSelectionModel().select(0);
		}

		Button reply = new Button("Reply");
		reply.setStyle("-fx-base: derive(cornflowerblue, 80%);");
		reply.disableProperty().bind(account.getSelectionModel().selectedIndexProperty().isEqualTo(-1));
		Button cancel = new Button("Cancel");

		TextArea text = new TextArea();
		text.setMinHeight(150);
		text.setPromptText("Write your response here.");
		if(ct.isReply()) { text.setText("+"+DatabaseManager.getChannel(ct.getChannelId()).getTitle()+" "); }

		HBox hbox = new HBox(10);
		hbox.setAlignment(Pos.CENTER_RIGHT);
		hbox.getChildren().addAll(reply, cancel);

		VBox vbox = new VBox(10);
		vbox.setId("stackMenu");
		vbox.setPadding(new Insets(25,25,25,25));
		vbox.setFillWidth(true);
		vbox.setMaxWidth(450);
		vbox.setMaxHeight(0);
		vbox.getChildren().addAll(account, text, hbox);

		StackPane stack = new StackPane();
		stack.setStyle("-fx-background-color: rgba(127,127,127,0.5);");
		stack.setAlignment(Pos.CENTER);
		stack.getChildren().add(vbox);

		Platform.runLater(() -> CommentSuiteFX.addOverlay(stack));
		cancel.setOnAction(ae -> Platform.runLater(() -> CommentSuiteFX.getApp().getMainStackPane().getChildren().remove(stack)));
		reply.setOnAction(ae -> {
			reply.setDisable(true);
			OA2Handler.postNewReply(ct.isReply() ? ct.getParentId() : ct.getId(), text.getText(), account.getValue());
			cancel.fire();
		});
	}
	
	private void viewFullComment() {
		ImageView img = new ImageView(BLANK_PROFILE);
		img.setFitHeight(32);
		img.setFitWidth(32);
		Image thumb = DatabaseManager.getChannel(ct.getChannelId()).fetchThumb();
		if(thumb != null) {
			img.setImage(thumb);
		}

		Hyperlink label = new Hyperlink(author.getText());
		label.setOnAction(author.getOnAction());

		HBox hbox = new HBox(5);
		hbox.setAlignment(Pos.CENTER_LEFT);
		hbox.getChildren().addAll(img, label);

		TextArea text = new TextArea(parsedText);
		text.setMaxHeight(300);
		text.setWrapText(true);
		text.setEditable(false);
		VBox.setVgrow(text, Priority.ALWAYS);

		Button close = new Button("Close");
		HBox hbox1 = new HBox(10);
		hbox1.setAlignment(Pos.CENTER_RIGHT);
		hbox1.getChildren().addAll(close);

		VBox vbox = new VBox(10);
		vbox.setMaxWidth(450);
		vbox.setAlignment(Pos.CENTER);
		vbox.setPadding(new Insets(25,25,25,25));
		vbox.setId("stackMenu");
		vbox.setFillWidth(true);
		vbox.getChildren().addAll(hbox, text, hbox1);

		VBox pad = new VBox();
		pad.setAlignment(Pos.CENTER);
		pad.setPadding(new Insets(50,25,50,25));
		pad.getChildren().add(vbox);

		StackPane stack = new StackPane();
		stack.setStyle("-fx-background-color: rgba(127,127,127,0.5);");
		stack.setAlignment(Pos.CENTER);
		stack.getChildren().add(pad);

		Platform.runLater(() -> CommentSuiteFX.addOverlay(stack));
		close.setOnAction(ae -> Platform.runLater(() -> CommentSuiteFX.getApp().getMainStackPane().getChildren().remove(stack)));
	}
	
	
	public void refreshImage() {
		ChannelType channel = DatabaseManager.getChannel(ct.getChannelId());
		if(channel.hasThumb())
			img.setImage(channel.fetchThumb());
	}
	
	private void loadProfileImage() throws SQLException {
		ChannelType channel = DatabaseManager.getChannel(ct.getChannelId());
		if(!channel.hasThumb()) {
			CommentSuiteFX.getApp().database.updateChannelFetchThumb(channel.getId(), true);
			channel = CommentSuiteFX.getApp().database.getChannelById(channel.getId());
			DatabaseManager.channelCache.put(channel.getId(), channel);
			CommentSuiteFX.getApp().refreshResultProfiles();
		}
	}
}
