package org.signal.model;

public class SignalData {
	private String account;
	private String sourceNumber; 
	private String sourceName;
	private long timestamp;

	private String groupId = null;
	private String groupName;
	private String sourceUuid;
	
	public String getAccount() {
		return account;
	}
	public void setAccount(String account) {
		this.account = account;
	}
	
	public String getSourceNumber() {
		return sourceNumber;
	}
	
	public void setSourceNumber(String sourceNumber) {
		this.sourceNumber = sourceNumber;
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
	
	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}
	
	public String getGroupId() {
		return groupId;
	}
	
	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}
	
	public String getGroupName() {
		return groupName;
	}
	
	public void setSourceUuid(String sourceUuid) {
		this.sourceUuid = sourceUuid;
	}
	
	public String getSourceUuid() {
		return sourceUuid;
	}
}
