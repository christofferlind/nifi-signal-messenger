package org.signal.model;

import com.google.gson.annotations.SerializedName;

public class SignalQuote {
	
	@SerializedName("quote-timestamp")
	private long timestamp;

	@SerializedName("quote-author")
	private String author;

	@SerializedName("quote-message")
	private String message;

	@SerializedName("quote-mention")
	private String mention;

	public SignalQuote(long timestamp, String author, String message, String mention) {
		this.timestamp = timestamp;
		this.author = author;
		this.message = message;
		this.mention = mention;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getAuthor() {
		return author;
	}

	public String getMessage() {
		return message;
	}

	public String getMention() {
		return mention;
	}
}
