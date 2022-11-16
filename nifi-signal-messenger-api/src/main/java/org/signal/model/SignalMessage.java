package org.signal.model;

public class SignalMessage {
	private String account;
	private String source; 
	private String sourceName;
	private long timestamp;
	private String message;
	
	private long expires = -1;
	private boolean viewOnce = false;
	private String groupId = null;
	
	public String getAccount() {
		return account;
	}
	public void setAccount(String account) {
		this.account = account;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
	public String getSourceName() {
		return sourceName;
	}
	public void setSourceName(String sourceName) {
		this.sourceName = sourceName;
	}
	public long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public long getExpires() {
		return expires;
	}
	public void setExpires(long expires) {
		this.expires = expires;
	}
	public boolean isViewOnce() {
		return viewOnce;
	}
	public void setViewOnce(boolean viewOnce) {
		this.viewOnce = viewOnce;
	}
	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}
	public String getGroupId() {
		return groupId;
	}
	
	@Override
	public String toString() {
		return String.format("Msg from %s to %s", getSource(), getAccount());
	}
}
