package org.signal.model;

public class SignalReaction extends SignalData {
	private String emoji;
	private String targetAuthor;
	private long targetSentTimestamp;
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
