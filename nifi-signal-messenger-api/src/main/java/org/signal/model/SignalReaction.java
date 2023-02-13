package org.signal.model;

import com.google.gson.annotations.SerializedName;

public class SignalReaction extends SignalData {
	
	@SerializedName("emoji")
	private String emoji = null;
	
	@SerializedName("targetAuthorNumber")
	private String targetAuthor = null;
	
	@SerializedName("targetSentTimestamp")
	private long targetSentTimestamp = -1;
	
	@SerializedName("isRemove")
	private boolean remove;
	
	public String getEmoji() {
		return emoji;
	}
	
	public void setEmoji(String emoji) {
		this.emoji = emoji;
	}
	
	@Override
	public String toString() {
		return String.format("Reaction (%s) from %s to %s", getEmoji(), getSource(), getAccount());
	}

	public void setTargetAutor(String targetAuthor) {
		this.targetAuthor = targetAuthor;
	}

	public String getTargetAuthor() {
		return targetAuthor;
	}

	public void setTargetSentTimestamp(long targetSentTimestamp) {
		this.targetSentTimestamp = targetSentTimestamp;
	}
	
	public long getTargetSentTimestamp() {
		return targetSentTimestamp;
	}

	public void setRemove(boolean remove) {
		this.remove = remove;
	}
	
	public boolean isRemove() {
		return remove;
	}
}
